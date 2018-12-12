import firebase_admin
import google.cloud
import googlemaps
import json
import copy
import binpacking
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

event_ref = None
destination = None

participants = {}


######################
def cluster_distance_voronoi(request):
    global event_ref
    global participants
    global destination

    drivers = []
    drivers_distance = ValueSortedDict()

    riders = []
    riders_distance = ValueSortedDict()

    rider_to_driver_distance = {}

    participants = {}

    cluster = {}

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
    # if event_uid is None:
    # 	event_uid = u'SBgh4MKtplFEbYXLvmMY'
    event_uid = request['eventUID']
    cars_parameter = request['cars']
    distance_parameter = request['distance']
    event_optimization = request['optimization']
    event_ref = db.collection(u'events').document(event_uid)
    participants_ref = db.collection(u'events').document(event_uid).collection(u'participants')
    print(u'Completed event: {}'.format(event_uid))
    print('Prioritization of minimizing cars: ' + str(cars_parameter))
    print('Prioritization of minimizing distance: ' + str(distance_parameter))
    print('------------------------------')
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
            drivers_distance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            cluster[p.id] = []
        else:
            riders.append(p.id)
            riders_distance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
        participants[p.id] = p

    for rider in riders:
        source = participants.get(rider).start.get(u'street')
        rider_to_driver_distance[rider] = {}
        for driver in drivers:
            destination = participants.get(driver).start.get(u'street')
            rider_to_driver_distance[rider][driver] = gmaps.distance_matrix(source, destination).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS

    print("Distances: {}".format(rider_to_driver_distance))
    group_best_match_riders(cluster, rider_to_driver_distance)
    print(u'Voronoi cluster: {}'.format(cluster))

    print('------------------------------')
    print('Initial values.')
    initial_distance = calculate_cluster_distance(cluster)
    print('Distance: ' + str(initial_distance))
    initial_cars = len(cluster)
    print('# Cars: ' + str(initial_cars) + '.')

    # ----------------------------------
    #
    # f(x)=(cars_parameter*(len(x)/initial_cars))+(distance_parameter*(calculate_cluster_distance(x)/initial_distance))
    #
    print('------------------------------')
    print('Calculating cluster minimizing cars.')
    cluster_cars = group_cells_cars(copy.deepcopy(cluster))
    print('cluster_cars: {}'.format(cluster_cars))
    distance_cars = calculate_cluster_distance(cluster_cars)
    print('Distance: ' + str(distance_cars))
    print('# Cars: ' + str(len(cluster_cars)) + '.')
    f_cluster_cars = (cars_parameter * (len(cluster_cars) / initial_cars)) + \
                     (distance_parameter * (distance_cars / initial_distance))
    print('function_cluster_cars: ' + str(f_cluster_cars) + '.')
    print('------------------------------')
    print('Calculating cluster minimizing distance.')
    cluster_distance = group_cells_distance(copy.deepcopy(cluster), drivers_distance)
    print('cluster_distance: {}'.format(cluster_distance))
    distance_distance = calculate_cluster_distance(cluster_distance)
    print('Distance: ' + str(distance_distance))
    print('# Cars: ' + str(len(cluster_distance)) + '.')
    f_cluster_distance = (cars_parameter * (len(cluster_distance) / initial_cars)) + \
                         (distance_parameter * (distance_distance / initial_distance))
    print('function_cluster_distance: ' + str(f_cluster_distance) + '.')

    if event_optimization:
        # call waypoint optimization method TODO
        pass
    update_database(cluster_cars) if f_cluster_cars < f_cluster_distance else update_database(cluster_distance)
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


##########################
def group_best_match_riders(cluster, rider_to_driver_distance):
    global participants
    for rider in rider_to_driver_distance:
        match = False
        while not match:
            best_distance = 0
            best_match = None
            for driver in rider_to_driver_distance[rider]:
                # TODO use a percentage calculation? best_route / nº nodes
                if rider_to_driver_distance[rider][driver] > best_distance:
                    best_distance = rider_to_driver_distance[rider][driver]
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
                del rider_to_driver_distance[rider][best_match]
                if not rider_to_driver_distance[rider]:  # is empty
                    match = True


######################
def group_cells_distance(cluster_distance, drivers):
    # calculate distances between the selected drivers
    driver_to_driver_distance = {}
    for driver in drivers:
        driver_to_driver_distance[driver] = {}
        source = participants.get(driver).start.get('street')
        for other_driver in drivers:
            if driver is other_driver:
                continue
            participant_location = participants.get(other_driver).start.get('street')
            driver_to_driver_distance[driver][other_driver] = \
                gmaps.distance_matrix(source, participant_location).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS
    # group cars with the best available option
    return group_best_match_drivers(cluster_distance, driver_to_driver_distance)


######################
def group_best_match_drivers(cluster_distance, driver_to_driver_distance):
    for driver in driver_to_driver_distance:
        match = False
        while not match:
            best_distance = 0
            best_match = None
            for new_driver in driver_to_driver_distance[driver]:
                # TODO use a percentage calculation? best_route / nº nodes
                if driver_to_driver_distance[driver][new_driver] > best_distance:
                    best_distance = driver_to_driver_distance[driver][new_driver]
                    best_match = new_driver
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if best_match in cluster_distance:
                cluster_length = len(cluster_distance.get(best_match))
            else:
                cluster_length = seats
            if seats > cluster_length:
                if verify_cumulative_distance(cluster_distance, best_match, driver):
                    # join cars
                    for rider in cluster_distance.get(driver):
                        cluster_distance[best_match].append(rider)
                    cluster_distance[best_match].append(driver)
                    # remove old car from available clusters
                    del cluster_distance[driver]
                    match = True
                else:
                    del driver_to_driver_distance[driver][best_match]
                    if not driver_to_driver_distance[driver]:  # is empty
                        match = True
            else:
                del driver_to_driver_distance[driver][best_match]
                if not driver_to_driver_distance[driver]:  # is empty
                    match = True
    return cluster_distance


######################
def verify_cumulative_distance(cluster, new_driver, old_driver):
    old_distance_old_driver = calculate_cluster_distance({new_driver: cluster.get(new_driver)})
    old_distance_new_driver = calculate_cluster_distance({old_driver: cluster.get(old_driver)})
    old_distance = old_distance_old_driver + old_distance_new_driver
    # TODO does the dictionary for the new_distance always old?
    new_distance = calculate_cluster_distance({new_driver: cluster.get(new_driver)+[old_driver]+cluster.get(old_driver)})
    if old_distance < new_distance:
        return False
    else:
        return True


######################
def group_cells_cars(cluster_cars):
    global participants
    # order cars by number of empty seats
    driver_seats = ValueSortedDict()
    driver_passengers = {}
    for driver in cluster_cars.keys():
        # get number of empty seats
        cluster_list = cluster_cars.get(driver)
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
            for rider in cluster_cars.get(old_driver):
                cluster_cars[list(driver_seats.keys())[-1]].append(rider)
            cluster_cars[list(driver_seats.keys())[-1]].append(old_driver)
            # remove old car from available clusters
            del cluster_cars[old_driver]
        # Repeat the bin packing algorithm with the next emptiest car and with the remaining passenger groups,
        # until no more packing is possible.
        if not copy_driver_passengers:  # evaluates to true when empty
            grouping = False  # end loop

    return cluster_cars


######################
def group_cells(cluster, drivers_distance):
    reset = True
    # TODO try to match full cars (2 empty + 2, instead of 2 empty +1)
    while reset:
        for driver in drivers_distance.keys():
            if driver in cluster:
                change = False
                for next_driver in drivers_distance.keys():
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
                        elif (len(cluster.get(driver)))+1 <=\
                                (participants.get(next_driver).get_seats() - cluster_list_length):
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
def calculate_cluster_distance(cluster):
    global participants, destination
    total_distance = 0
    for driver in cluster.keys():
        waypoints = []
        for rider in cluster.get(driver):
            waypoints.append(participants.get(rider).start.get(u'street'))
        origin = participants.get(driver).start.get(u'street')
        print('origin: {}, waypoints: {}, destination: {}'.format(origin, waypoints, destination))
        # optimizing waypoints to get best route
        direction_results = gmaps.directions(origin, destination,
                                             mode="driving", waypoints=waypoints, alternatives=True, avoid=None,
                                             language=None, units="metric", region=None, departure_time=None,
                                             arrival_time=None, optimize_waypoints=True, transit_mode=None,
                                             transit_routing_preference=None, traffic_model=None)
        # if there are waypoints then there are several legs to consider
        for i in range(len(waypoints)+1):
            total_distance += direction_results[0].get(u'legs')[i].get(u'distance').get(u'value')
    return total_distance


#########################
def update_database(cluster):
    event_ref.update({u'completed': True})
    event_ref.update({u'cluster': firestore.DELETE_FIELD})  # make sure there is no old field (SHOULDN'T HAPPEN)
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
