import firebase_admin, google.cloud, googlemaps, json
import binpacking
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

event_ref = None

drivers = []
driversDirections = {}
driversDistance = ValueSortedDict()

riders = []
ridersDirections = {}
ridersDistance = ValueSortedDict()

RtoDDistance= {}

participants = {}

cluster = {}


######################
def cluster_distance_voronoi(request):
    global event_ref
    global drivers, driversDistance, driversDirections
    global riders, ridersDistance, ridersDirections
    global participants, RtoDDistance, cluster

    drivers = []
    driversDirections = {}
    driversDistance = ValueSortedDict()

    riders = []
    ridersDirections = {}
    ridersDistance = ValueSortedDict()

    RtoDDistance = {}

    participants = {}

    cluster = {}

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
    # if event_uid is None:
    # 	event_uid = u'SBgh4MKtplFEbYXLvmMY'
    event_uid = request['eventUID']
    event_mode = request['mode']
    event_optimization = request['optimization']
    event_ref = db.collection(u'events').document(event_uid)
    participants_ref = db.collection(u'events').document(event_uid).collection(u'participants')
    print(u'Completed event: {}'.format(event_uid))
    try:
        event = Event(**event_ref.get().to_dict())
        event_participants = participants_ref.get()
    except google.cloud.exceptions.NotFound:
        print(u'No such document')
        return

    destination = event.destination.get(u'street')
    for participant in event_participants:
        p = Participant(**participant.to_dict())
        p.set_id(participant.id)
        source = p.start.get(u'street')
        distance_results = gmaps.distance_matrix(source, destination)  # TODO this can result ZERO_RESULTS
        if p.is_driver():
            drivers.append(p.id)
            driversDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            cluster[p.id] = []
        else:
            riders.append(p.id)
            ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
        participants[p.id] = p

    for rider in riders:
        source = participants.get(rider).start.get(u'street')
        RtoDDistance[rider] = {}
        for driver in drivers:
            destination = participants.get(driver).start.get(u'street')
            RtoDDistance[rider][driver] = gmaps.distance_matrix(source, destination).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS

    print("Distances: {}".format(RtoDDistance))
    group_best_match()
    print(u'Voronoi cluster: {}'.format(cluster))
    if event_mode == 'cars':
        print("Grouping cells by reducing number of cars.")
        group_cells_cars()
    elif event_mode == 'distance':
        print("Grouping cells by reducing distance travelled")
        group_cells_distance()
    else:  # fail-safe
        print("Grouping with fail-safe")
        group_cells()
    print(u'Final clusters: {}'.format(cluster))
    if event_optimization:
        # call waypoint optimization method
        pass
    update_database()
    #  return cluster
    #  return 'OK'
    #  data = {'response': 'OK'}
    #  return json.dumps(data)


##########################
def group_best_match():
    global participants
    for rider in RtoDDistance:
        match = False
        while not match:
            best_distance = 0
            best_match = None
            for driver in RtoDDistance[rider]:
                # TODO use a percentage calculation? best_route / nº nodes
                if RtoDDistance[rider][driver] > best_distance:
                    best_distance= RtoDDistance[rider][driver]
                    best_match = driver
            if best_match in cluster:
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = 0
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if seats > cluster_length:
                cluster[best_match].append(rider)
                match = True
            else:
                del RtoDDistance[rider][best_match]
                if not RtoDDistance[rider]:  # is empty
                    match = True


######################
def group_cells_distance():
    # calculate distances between the selected drivers
    DtoDDistance = {}
    for driver in drivers:
        DtoDDistance[driver] = {}
        source = participants.get(driver).start.get('street')
        for other_driver in drivers:
            if driver is other_driver:
                continue
            destination = participants.get(other_driver).start.get('street')
            DtoDDistance[driver][other_driver] = gmaps.distance_matrix(source, destination).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS
    # group cars with the best available option
    group_best_match_drivers(DtoDDistance)


######################
def group_best_match_drivers(DtoDDistance):
    for driver in DtoDDistance:
        match = False
        while not match:
            best_distance = 0
            best_match = None
            for new_driver in DtoDDistance[driver]:
                # TODO use a percentage calculation? best_route / nº nodes
                if DtoDDistance[driver][new_driver] > best_distance:
                    best_distance = DtoDDistance[driver][new_driver]
                    best_match = new_driver
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if best_match in cluster:
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = seats
            if seats > cluster_length:
                # join cars
                for rider in cluster.get(driver):
                    cluster[best_match].append(rider)
                cluster[best_match].append(driver)
                # remove old car from available clusters
                del cluster[driver]
                match = True
            else:
                del DtoDDistance[driver][best_match]
                if not DtoDDistance[driver]:  # is empty
                    match = True


######################
def group_cells_cars():
    # order cars by number of empty seats
    driver_seats = ValueSortedDict()
    driver_passengers = {}
    for driver in cluster.keys():
        # get number of empty seats
        cluster_list = cluster.get(driver)
        cluster_list_length = len(cluster_list)
        driver_seats[driver] = participants.get(driver).get_seats() - cluster_list_length
        # get the number of passenger + the driver
        driver_passengers[driver] = cluster_list_length + 1
    grouping = True
    while grouping:
        # Run the bin packing algorithm with bins of capacity equal to that of the car with more available seats.
        #    The car with the most empty seats must not be an item of the the bin packing
        copy_driver_passengers = driver_passengers.copy()
        del copy_driver_passengers[list(driver_seats.keys())[-1]]
        #    Calculate the bin packing solution
        bins = binpacking.to_constant_volume(copy_driver_passengers, list(driver_seats.values())[-1])
        # Of the bins produced by the algorithm, choose the bin with more items in it.
        biggest_bin = max(enumerate(bins), key=lambda tup: len(tup[1]))[1]
        # Remove the grouped cars (the bin + the items) from the sorted list and from the unplaced items.
        for driver in biggest_bin.keys():
            del driver_seats[driver]
            del driver_passengers[driver]
        # Update cluster. The biggest bin as passengers of the driver with more empty seats
        for old_driver in biggest_bin:  # join cars
            for rider in cluster.get(old_driver):
                cluster[list(driver_seats.keys())[-1]].append(rider)
            cluster[list(driver_seats.keys())[-1]].append(old_driver)
            # remove old car from available clusters
            del cluster[old_driver]
        # Repeat the bin packing algorithm with the next emptiest car and with the remaining passenger groups,
        # until no more packing is possible.
        if not copy_driver_passengers:  # evaluates to true when empty
            grouping = False  # end loop


######################
def group_cells():
    reset = True
    # TODO try to match full cars (2 empty + 2, instead of 2 empty +1)
    while reset:
        for driver in driversDistance.keys():
            if driver in cluster:
                change = False
                for next_driver in driversDistance.keys():
                    if next_driver in cluster:
                        cluster_list = cluster.get(next_driver)
                        cluster_list_length = len(cluster_list)
                        # spiderman meme pointing at himself
                        if participants.get(next_driver) == participants.get(driver):
                            reset = False
                            continue
                        # driver already has a full car
                        elif participants.get(next_driver).get_seats() <= cluster_list_length:
                            reset = False
                            continue
                        # driver has available seats
                        elif (len(cluster.get(driver)))+1 <= (participants.get(next_driver).get_seats() - cluster_list_length):
                            # TODO match full car
                            # TODO only match if round trip isn't bigger than separate trip
                            # join cars
                            for rider in cluster.get(driver):
                                cluster[next_driver].append(rider)
                            cluster[next_driver].append(driver)
                            # remove old car from available clusters
                            del cluster[driver]
                            # improvement was possible, so lets reset to search for more
                            change = True
                            reset = True
                        else:  # any other limit conditions?
                            # nothing was done so no more improvements were possible
                            reset = False
                if change:
                    break


#########################
def update_database():
    event_ref.update({u'completed': True})
    event_ref.update({u'cluster': firestore.DELETE_FIELD}) # make sure there is no old field (SHOULDN'T HAPPEN)
    for driver in cluster.keys():
        cluster_riders = []
        for rider in cluster.get(driver):
            rider_ref = db.collection(u'users').document(rider)
            cluster_riders.append(rider_ref)
        event_ref.update({u'cluster.'+driver: cluster_riders})


#######################
class Participant(object):

    id = None
    seats = 0

    def __init__(self, **fields):
        self.__dict__.update(fields)

    def to_dict(self):
        if not self.driver:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': False}
        else:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': True, u'seats': self.seats}
        return dictionary

    def is_driver(self):
        if self.driver:
            return True
        else:
            return False

    def set_id(self, id):
        self.id = id

    def set_seats(self, seats):
        self.seats = seats

    def get_seats(self):
        return int(self.seats)


#######################
class Event(object):
    def __init__(self, **fields):
        self.__dict__.update(fields)


#######################
def read_json(file):
    try:
        print('Reading from input')
        with open(file, 'r') as f:
            return json.load(f)
    finally:
        print('Done reading')


if __name__ == '__main__':
    return_dict = read_json("request.json")
    cluster_distance_voronoi(return_dict)
