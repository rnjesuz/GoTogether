import firebase_admin, google.cloud, googlemaps, json
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions
from haversine import haversine
from math import radians, cos, sin, asin, sqrt

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
def cluster_distance_radius(request):
    global event_ref
    global drivers, driversDistance, driversDirections
    global riders, ridersDistance, ridersDirections
    global participants, RtoDRouteShare, cluster

    drivers = []
    driversDirections = {}
    driversDistance = ValueSortedDict()

    riders = []
    ridersDirections = {}
    ridersDistance = ValueSortedDict()

    RtoDRouteShare = {}

    participants = {}

    cluster = {}

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
    # if event_uid is None:
    # 	event_uid = u'SBgh4MKtplFEbYXLvmMY'
    event_uid = request['eventUID']
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

    radius = 10  # Kilometers

    matches = 0
    while len(ridersDistance.keys()) > matches:
        for driver in driversDistance:
            participant = participants.get(driver)
            seats = participant.get_seats()
            possible_riders = riders.copy()
            riders_range = len(possible_riders)
            for i in range(riders_range):
                driver_cluster_len = len(cluster.get(driver))
                if driver_cluster_len < seats:
                    if is_inside_radius(participants.get(driver).start.get(u'street'), participants.get(possible_riders[i]).start.get(u'street'), radius):
                        print(u'Group! rider {} grouped with driver {}.'.format(possible_riders[i], driver))
                        cluster[driver].append(possible_riders[i])
                        riders.remove(possible_riders[i])
                        matches += 1
                    else:
                        print(u'NO Group! rider {} not grouped with driver {}.'.format(possible_riders[i], driver))
        print('Grouped: {}'.format(cluster))
        print('Remaining riders: {}'.format(riders))
        radius = radius + 2  # increase radius by 2 Km and repeat

    print(u'Initial grouping: {}'.format(cluster))
    group_cells()
    print(u'Created clusters: {}'.format(cluster))
    update_database()
    #  return cluster
    #  return 'OK'
    #  data = {'response': 'OK'}
    #  return json.dumps(data)


######################
def is_inside_radius(driver_source, rider_source, radius):
    driver_geocode_results = gmaps.geocode(driver_source)
    rider_geocode_results = gmaps.geocode(rider_source)
    # haversine_distance = haversine_formula(driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
    #                               driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lng'),
    #                               rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
    #                               rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lng'))
    haversine_distance = haversine((driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
                                  driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lng')),
                                  (rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
                                  rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lng')))
    if radius >= haversine_distance:
        print(u'Radius of {} larger than Haversine distance of {}.'.format(radius, haversine_distance))
        return True
    else:
        print(u'Radius of {} smaller than Haversine distance of {}.'.format(radius, haversine_distance))
        return False


######################
def haversine_formula(lon1, lat1, lon2, lat2):
    """
    Calculate the great circle distance between two points
    on the earth (specified in decimal degrees)
    """
    # convert decimal degrees to radians
    lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])

    # haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    r = 6371  # Radius of earth in kilometers. Use 3956 for miles
    return c * r


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
    cluster_distance_radius(return_dict)
