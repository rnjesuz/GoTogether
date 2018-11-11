import firebase_admin, google.cloud, googlemaps, json
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

cluster = {}


######################
def cluster_route(request):
    global event_ref
    global drivers, driversDistance, driversDirections
    global riders, ridersDistance, ridersDirections
    global participants, RtoDRouteShare, cluster

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
    # if event_uid is None:
    # 	event_uid = u'SBgh4MKtplFEbYXLvmMY'
    event_uid = 'SBgh4MKtplFEbYXLvmMY'
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
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            driversDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            ridersDirections[p.id] = direction_results
        participants[p.id] = p

    for rider in ridersDistance.keys():
        RtoDRouteShare[rider] = {}
        for driver in driversDistance.keys():
            RtoDRouteShare[rider][driver] = get_shared_path(rider, driver)

    print("Routes: {}".format(RtoDRouteShare))
    group_best_match()
    print("Route cluster: {}".format(cluster))
    group_cells()
    print(u'Created cluster: {}'.format(cluster))
    update_database()
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


###########################
def get_shared_path(rider, driver):
    share = 0
    r_directions = ridersDirections.get(rider)[0].get('legs')[0].get('steps')
    d_directions = driversDirections.get(driver)[0].get('legs')[0].get('steps')
    riders_directions_range = len(r_directions)
    drivers_directions_range = len(d_directions)
    for r_steps in range(riders_directions_range):
        for d_steps in range(drivers_directions_range):
            if (r_directions[r_steps].get('start_location') == d_directions[d_steps].get('start_location')) and (r_directions[r_steps].get('end_location') == d_directions[d_steps].get('end_location')):
                share = share+1
    return share


##########################
def group_best_match():
    global participants
    for rider in RtoDRouteShare:
        match = False
        while not match:
            best_route = 0
            best_match = None
            # TODO if no driver left in list then return exception
            for driver in RtoDRouteShare[rider]:
                # TODO use a percentage calculation? best_route / nº nodes
                if RtoDRouteShare[rider][driver] > best_route:
                    best_route = RtoDRouteShare[rider][driver]
                    best_match = driver
            if cluster.get(best_match):
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = 0
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if seats >= cluster_length:
                cluster[best_match].append(rider)
                match = True
            else:
                del RtoDRouteShare[rider][best_match]


######################
def group_cells():
    reset = True
    # TODO try to match full cars (2 empty + 2, instead of 2 empty +1)
    while reset:
        for driver in driversDistance.keys():
            if driver in cluster:
                change = False
                for next_driver in driversDistance.keys():
                    if cluster.get(next_driver):
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
