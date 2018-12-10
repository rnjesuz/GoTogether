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

RtoDRouteShare = {}

participants = {}
participantsDirections = {}



######################
def cluster_route(request):
    global event_ref
    global drivers, driversDistance, driversDirections
    global riders, ridersDistance, ridersDirections
    global participants, participantsDirections
    global RtoDRouteShare

    drivers = []
    driversDirections = {}
    driversDistance = ValueSortedDict()

    riders = []
    ridersDirections = {}
    ridersDistance = ValueSortedDict()

    RtoDRouteShare = {}

    participants = {}
    participantsDirections = {}

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
        # distance_results = gmaps.distance_matrix(source, destination)  # TODO this can result ZERO_RESULTS
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            # driversDistance[p.id]= distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            driversDistance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            # ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            ridersDistance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # ridersDirections[p.id] = direction_results
        participants[p.id] = p
        participantsDirections[p.id] = direction_results

    for rider in ridersDistance.keys():
        RtoDRouteShare[rider] = {}
        for driver in driversDistance.keys():
            RtoDRouteShare[rider][driver] = get_shared_path(rider, driver)

    print("Shared Nodes: {}".format(RtoDRouteShare))
    group_best_match_riders(cluster)
    print("Route clusters: {}".format(cluster))
    if event_mode == 'cars':
        print("Grouping cells by reducing number of cars.")
        group_cells_cars(cluster)
    elif event_mode == 'distance':
        print("Grouping cells by reducing distance travelled")
        group_cells_distance(cluster)
    else:  # fail-safe
        print("Grouping with fail-safe")
        group_cells(cluster)
    print(u'Final clusters: {}'.format(cluster))
    if event_optimization:
        # call waypoint optimization method
        pass
    update_database(cluster)
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


###########################
def get_shared_path(first_participant, second_participant):
    share = 0
    f_directions = participantsDirections.get(first_participant)[0].get('legs')[0].get('steps')
    s_directions = participantsDirections.get(second_participant)[0].get('legs')[0].get('steps')
    for r_steps in range(len(f_directions)):
        for d_steps in range(len(s_directions)):
            if (f_directions[r_steps].get('start_location') == s_directions[d_steps].get('start_location')) and (f_directions[r_steps].get('end_location') == s_directions[d_steps].get('end_location')):
                share = share+1
    return share


##########################
def group_best_match_riders(cluster):
    global participants
    for rider in RtoDRouteShare:
        match = False
        while not match:
            best_route = 0
            best_match = None
            for driver in RtoDRouteShare[rider]:
                # TODO use a percentage calculation? best_route / number of nodes
                if RtoDRouteShare[rider][driver] > best_route:
                    best_route = RtoDRouteShare[rider][driver]
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
                del RtoDRouteShare[rider][best_match]
                if not RtoDRouteShare[rider]:  # is empty
                    match = True


######################
def group_cells_distance(cluster):
    # calculate route shared with other drivers, till the destination
    DtoDRouteShare = {}
    for driver in driversDistance.keys():
        DtoDRouteShare[driver] = {}
        for other_driver in driversDistance.keys():
            if driver is other_driver:
                continue
            DtoDRouteShare[driver][other_driver] = get_shared_path(driver, other_driver)
    # group cars with the best available option
    group_best_match_drivers(DtoDRouteShare, cluster)


######################
def group_best_match_drivers(DtoDRouteShare, cluster):
    global participants
    for driver in DtoDRouteShare:
        match = False
        while not match:
            best_route = 0
            best_match = None
            for new_driver in DtoDRouteShare[driver]:
                # TODO use a percentage calculation? best_route / number of nodes
                if DtoDRouteShare[driver][new_driver] > best_route:
                    best_route = DtoDRouteShare[driver][new_driver]
                    best_match = new_driver
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if best_match in cluster:
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = seats
            if seats > cluster_length + len(cluster.get(driver)) + 1:
                # join cars
                for rider in cluster.get(driver):
                    cluster[best_match].append(rider)
                cluster[best_match].append(driver)
                # remove old car from available clusters
                del cluster[driver]
                match = True
            else:
                del DtoDRouteShare[driver][best_match]
                if not DtoDRouteShare[driver]:  # is empty
                    match = True


######################
def group_cells_cars(cluster):
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
        biggest_bin = max(enumerate(bins), key = lambda tup: len(tup[1]))[1]
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
def group_cells(cluster):
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
                        # looking in the mirror
                        if participants.get(next_driver) == participants.get(driver):
                            reset = False
                            continue
                        # driver already has a full car
                        elif participants.get(next_driver).get_seats() <= cluster_list_length:
                            reset = False
                            continue
                        # driver has available seats
                        # #(riders)+driver <= (possible car).seats - already occupied seats
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
def update_database(cluster):
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
    cluster_route(return_dict)
