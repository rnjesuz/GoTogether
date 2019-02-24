import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
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
destination = None

participants = {}


######################
def cluster_distance_radius(request):
    """
        Calculates the best possible cluster, applying an heuristic based on percentage of route shared

        The algorithm splits participants in riders and drivers
        Then, the riders are matched with the best available driver based on the heuristic
        Then, drivers are matched between themselves:
            A cluster is calculated to minimize cars using a bin-packing algorithm
            A cluster is calculated to minimize total distance using the heuristic
            A weight function is applied to each cluster
            f(x)=(cars_parameter*(len(x)/initial_cars))+(distance_parameter*(distance(x)/initial_distance))
            Both functions are compared and the best cluster is chosen
        The database is updated with the final cluster

        :param request: a formatted request with values for the algorithm
                eventUID (String): The name of the event, from which to calculate the cluster
                cars (float): How much weight given to minimizing number of cars. Cars = 100 - distance
                distance (float): How much weight given to minimizing total distance traveled. Distance = 100 - cars
                optimization (boolean): Optimize the order of the riders in each car? *TODO*
        :type request: JSON

        :return: None

        TODOs
            Complete implementation of module using 'optimization' parameter
        """
    global event_ref
    global participants
    global destination

    drivers = []
    drivers_distance = ValueSortedDict()

    riders = []
    riders_distance = ValueSortedDict()

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
            # drivers_distance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            cluster[p.id] = []
        else:
            riders.append(p.id)
            # riders_distance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
        participants[p.id] = p

    radius = 5  # Kilometers
    matches = 0
    while len(riders) > matches:
        for driver in drivers:
            participant = participants.get(driver)
            seats = participant.get_seats()
            possible_riders = riders.copy()
            riders_range = len(possible_riders)
            for i in range(riders_range):
                driver_cluster_len = len(cluster.get(driver))
                if driver_cluster_len < seats:
                    if is_inside_radius(participants.get(driver).start.get(u'LatLng'),
                                        participants.get(possible_riders[i]).start.get(u'LatLng'),
                                        radius):
                        print(u'Group! Rider \'{}\' grouped with driver \'{}\'.'.format(possible_riders[i], driver))
                        cluster[driver].append(possible_riders[i])
                        riders.remove(possible_riders[i])
                        matches += 1
                    else:
                        print(u'NO Group! Outside radius')
                else:
                    print(u'NO Group! Insufficient seats')
        print('Remaining riders: {}'.format(riders))
        print('Increasing radius')
        radius += 5  # increase radius by X Km and repeat

    print(u'Radial clusters: {}'.format(cluster))

    print('------------------------------')
    print('Calculating INITIAL values.')
    initial_cars = len(cluster)
    print('Initial number of  cars: ' + str(initial_cars) + '.')
    initial_distance = calculate_cluster_distance(cluster)
    print('Total initial distance: ' + str(initial_distance))

    # ----------------------------------
    #
    # f(x)=(cars_parameter*(len(x)/initial_cars))+(distance_parameter*(distance(x)/initial_distance))
    #
    print('------------------------------')
    print('Calculating cluster minimizing CARS.')
    cluster_cars = group_cells_cars(copy.deepcopy(cluster))
    distance_cars = calculate_cluster_distance(cluster_cars)
    f_cluster_cars = (cars_parameter * (len(cluster_cars) / initial_cars)) + \
                     (distance_parameter * (distance_cars / initial_distance))
    print('------------------------------')
    print('Calculating cluster minimizing DISTANCE.')
    cluster_distance = group_cells_distance(copy.deepcopy(cluster), drivers_distance, drivers)
    distance_distance = calculate_cluster_distance(cluster_distance)
    f_cluster_distance = (cars_parameter * (len(cluster_distance) / initial_cars)) + \
                         (distance_parameter * (distance_distance / initial_distance))
    print('------------------------------')
    print('Initial values.')
    print("Route clusters: {}".format(cluster))
    print('# Cars: ' + str(initial_cars) + '.')
    print('Distance: ' + str(initial_distance))
    print('------------------------------')
    print('Cluster by min of cars')
    print('Cluster: {}'.format(cluster_cars))
    print('# of Cars: ' + str(len(cluster_cars)) + '.')
    print('Distance: ' + str(distance_cars))
    print('function_cluster_cars: ' + str(f_cluster_cars) + '.')
    print('------------------------------')
    print('Cluster by min distance')
    print('Cluster: {}'.format(cluster_distance))
    print('# Cars: ' + str(len(cluster_distance)) + '.')
    print('Distance: ' + str(distance_distance))
    print('function_cluster_distance: ' + str(f_cluster_distance) + '.')

    if event_optimization:
        # call waypoint optimization method TODO
        pass
    print('------------------------------')
    if f_cluster_cars < f_cluster_distance:
        print('Choosing f_cars')
        print('Final Cluster: {}'.format(cluster_cars))
        update_database(cluster_cars)
    else:
        print('Choosing f_distance')
        print('Final Cluster: {}'.format(cluster_distance))
        update_database(cluster_distance)
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


######################
def is_inside_radius(driver_source, rider_source, radius):
    # driver_geocode_results = gmaps.geocode(driver_source)
    # rider_geocode_results = gmaps.geocode(rider_source)
    # haversine_distance = haversine_formula(driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
    #                               driver_geocode_results[0].get(u'geometry').get(u'location').get(u'lng'),
    #                               rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lat'),
    #                               rider_geocode_results[0].get(u'geometry').get(u'location').get(u'lng'))
    haversine_distance = haversine((driver_source.latitude, driver_source.longitude),
                                   (rider_source.latitude, rider_source.longitude))
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
def group_cells_distance(cluster_distance, drivers_distance, drivers):
    radius = 10  # Kilometers
    for driver in drivers_distance:
        print('DRIVER: {}'.format(driver))
        if driver in drivers:
            reset = True
            while reset:
                possible_drivers = drivers.copy()
                drivers_range = len(possible_drivers)
                driver_cluster_len = len(cluster_distance.get(driver))
                for i in range(drivers_range):
                    # it's the same individual
                    if driver is possible_drivers[i]:
                        reset = False
                        continue
                    participant = participants.get(possible_drivers[i])
                    seats = participant.get_seats()
                    if driver_cluster_len < seats - (len(cluster_distance.get(possible_drivers[i])) + 1):
                        if is_inside_radius(participants.get(driver).start.get(u'LatLng'),
                                            participants.get(possible_drivers[i]).start.get(u'LatLng'), radius):
                            # TODO match if roundtrip is smaller
                            # see if the cumulative distance of the join is better than the separate voyages
                            if verify_cumulative_distance(cluster_distance, possible_drivers[i], driver):
                                print(u'Group! Driver \'{}\' grouped with new driver \'{}\'.'
                                      .format(driver, possible_drivers[i]))
                                for rider in cluster_distance.get(driver) or []:
                                    cluster_distance[possible_drivers[i]].append(rider)
                                cluster_distance[possible_drivers[i]].append(driver)
                                drivers.remove(driver)
                                del cluster_distance[driver]
                                reset = False
                                # Break the inner loop
                                break
                            else:
                                reset = False
                                print(u'NO Group! Roundtrip is longer.')
                        else:
                            reset = True
                            # print(u'NO Group! Outside radius.')
                    else:
                        reset = False
                        print(u'NO Group! Insufficient seats.')
                else:
                    # Continue if the inner loop wasn't broken
                    # print('Increasing radius')
                    radius = radius + 10  # increase radius by 2 Km and repeat
                    continue
                # Inner loop was broken, break the outer
                break
            print('Remaining drivers: {}'.format(drivers))
    return cluster_distance


######################
def verify_cumulative_distance(cluster, new_driver, old_driver):
    """
    Verifies if, based on the cluster, two drivers should travel together of separately

    Individually calculate the travelled distance of both drivers with their passengers to the destination
    Add them together to obtain the total distance travelled, if separate
    Calculate the distance the new driver travels by picking up its passengers and the old_driver + riders
    This gives the distance travelled together
    Compare the two

    :param cluster: the set with drivers and respective riders
    :type cluster: dict
    :param new_driver: the unique identifier of one driver. Must be present in the cluster.
    :type new_driver: str
    :param old_driver: the unique identifier of the other driver. Must be present on the cluster.
    :type old_driver: str
    :return: True if the distance travelled is shorter or equal together, False if its shorter separate.
    :rtype bool
    """
    print("        Separate distance:")
    old_distance_new_driver = calculate_cluster_distance({new_driver: cluster.get(new_driver)})
    old_distance_old_driver = calculate_cluster_distance({old_driver: cluster.get(old_driver)})
    old_distance = old_distance_old_driver + old_distance_new_driver
    print("        Total: " + str(old_distance))
    # TODO does the dictionary for the new_distance always old?
    print("        Joined distance:")
    new_distance = calculate_cluster_distance(
        {new_driver: cluster.get(new_driver) + [old_driver] + cluster.get(old_driver)})
    print("        Total: " + str(new_distance))
    if old_distance < new_distance:
        print('    A: Separate')
        return False
    else:
        print('    A: Joined')
        return True


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
def group_cells_cars(cluster_cars):
    global participants
    # order cars by number of empty seats
    driver_seats = ValueSortedDict()
    driver_passengers = {}
    print("Starting cluster: {}".format(cluster_cars))
    for driver in cluster_cars.keys():
        # get number of empty seats
        cluster_list = cluster_cars.get(driver)
        cluster_list_length = len(cluster_list)
        driver_seats[driver] = participants.get(driver).get_seats() - cluster_list_length
        # get the number of passenger + the driver
        driver_passengers[driver] = cluster_list_length + 1
    print("car OCCUPANCY: {}".format(driver_passengers))
    print("Car VACANCY: {}".format(dict(driver_seats)))

    grouping = True
    while grouping:

        # check if there's any cars still available to group
        if not driver_seats:  # evaluates to true when empty
            grouping = False  # end loop
            continue  # exit current iteration

        # Run the bin packing algorithm with bins of capacity equal to that of the car with more available seats.
        #    The car with the most empty seats must not be an item of the the bin packing
        copy_driver_passengers = driver_passengers.copy()
        new_driver = list(driver_seats.keys())[-1]
        del copy_driver_passengers[new_driver]
        driver_passengers_tuple = [(k, v) for k, v in copy_driver_passengers.items()]
        #    Order the cars based on the heuristic
        driver_passengers_tuple = order_by_heuristic(new_driver, driver_passengers_tuple)
        print(driver_passengers_tuple)
        #    Calculate the bin packing solution
        available_seats = list(driver_seats.values())[-1]
        if (not driver_passengers_tuple) == False:
            bins = binpacking.to_constant_volume(driver_passengers_tuple, available_seats, 1, -1, available_seats + 1)
        else:
            del driver_seats[new_driver]
            continue
        print(bins)
        # if the first position is empty it means ALL the values given are bigger than then bin size
        # e.g. bin_size = 2 & bin_packing = [{}, {"A": 6}]
        if not bins[0]:
            del driver_seats[new_driver]  # so we remove this driver from cars with available seats
            continue

        possible_best_bin = {}
        possible_best_bin_index = 0
        for b in bins:
            new_passengers = 0
            for passengers in b:
                # if the calculations had a car with more people than available seats ...
                # e.g. bin_size = 2 & bin_packing = [{"A": 1}, {"B": 6}]
                # bin_size = 2 & bin_packing = [{"A": 1, "B": 2}] ---> doesn't happen
                if passengers[1] > available_seats:
                    continue  # ... we skip it
                new_passengers += passengers[1]
            possible_best_bin[possible_best_bin_index] = new_passengers
            possible_best_bin_index += 1
        print(possible_best_bin)
        bins_with_more_passengers = [u for u, v in possible_best_bin.items() if
                                     int(v) >= max(possible_best_bin.values())]
        print(bins_with_more_passengers)
        best_bins = [bins[x] for x in bins_with_more_passengers]
        print(best_bins)

        # If multiple solutions exist...
        if len(best_bins) > 1:
            # ... compare them by distance...
            bin_distances = []
            for bin in best_bins:
                cluster_bins = {new_driver: []}
                for participant in bin:
                    cluster_bins[new_driver].append(participant[0])  # picking up the driver
                    cluster_bins[new_driver] += cluster_cars[participant[0]]  # and it's passenger
                bin_distances.append(calculate_cluster_distance(cluster_bins))
            # ... and pick the one with the smallest distance (if tied between several - choose any)
            better_bin = tuple(best_bins)[bin_distances.index(min(bin_distances))]
        else:
            better_bin = next(iter(best_bins))
        print(better_bin)
        print_cluster = []
        for i in range(0, better_bin.__len__()):
            driver_of_bin = better_bin[i][0]
            print_cluster += [driver_of_bin] + cluster_cars[driver_of_bin]
        print("Best bin for {}: {}".format(new_driver, print_cluster))

        # Remove the grouped cars (the bin + the items) from the sorted list and from the unplaced items.
        for driver in better_bin:
            del driver_seats[driver[0]]
            del driver_passengers[driver[0]]
        # Remove the receiving driver from the sorted list and from the unplaced items.
        del driver_seats[new_driver]
        del driver_passengers[new_driver]
        # Update cluster. The biggest bin as passengers of the driver with more empty seats
        for old_driver in better_bin:  # join cars
            for rider in cluster_cars.get(old_driver[0]):
                cluster_cars[new_driver].append(rider)
            cluster_cars[new_driver].append(old_driver[0])
            # remove old car from available clusters
            del cluster_cars[old_driver[0]]
        # Repeat the bin packing algorithm with the next emptiest car and with the remaining passenger groups,
        # until no more packing is possible.

    print("Calculated cluster: {}".format(cluster_cars))
    return cluster_cars


######################
def order_by_heuristic(driver, driver_passengers_tuple):
    """
    Orders the set after calculating values based on the heuristic

    Calculates the heuristic value between the driver and each element of the set
    Adds each calculated value to the last position of each element of the set
    Orders the set based on its stored parameter first and then the heuristic value

    :param driver: the unique identifier of the driver
    :type driver: str
    :param driver_passengers_tuple: a list of tuples containing several participants and their seats
    :return: the new set, ordered, with the new heuristic values
    :rtype list of tuples
    """

    index = 0
    radius = 5
    driver_geopoint = participants.get(driver).start.get(u'LatLng')
    # Get radius value that encompasses the possible match
    for match in driver_passengers_tuple:
        if driver == match[0]:
            continue
        match_geopoint = participants.get(match[0]).start.get(u'LatLng')
        # Get the haversine formula between driver and match
        distance = haversine((driver_geopoint.latitude, driver_geopoint.longitude),
                             (match_geopoint.latitude, match_geopoint.longitude))
        # Round up to the next multiple of 'radius'. Multiples of 'radius' stay the same
        radius_distance = ((distance+(radius-1))//radius)*radius
        # Create new tuple with 'radius_distance' in the last position
        driver_passengers_tuple[index] = (*driver_passengers_tuple[index], radius_distance)
        index += 1
    # Order the tuple
    #     The key = lambda x: (x[1], x[2]) should be read as:
    #     "firstly order by the seats in x[1] and then by the radius value in x[2]".
    driver_passengers_tuple = sorted(driver_passengers_tuple, key=lambda x: (x[1], x[2]))
    return driver_passengers_tuple


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
def update_database(cluster):
    """
    Updates the database with the given cluster

    :param cluster: the cluster of drivers and matched riders
    :type dict
    :return: None
    """
    event_ref.update({u'completed': True})

    event_ref.update({u'cluster': firestore.DELETE_FIELD})  # make sure there is no old field (SHOULDN'T HAPPEN)
    for driver in cluster.keys():
        cluster_riders = []
        for rider in cluster.get(driver):
            rider_ref = db.collection(u'users').document(rider)
            cluster_riders.append(rider_ref)
        event_ref.update({u'cluster.' + driver: cluster_riders})


#######################
class Participant(object):
    """
    This class is used as a representation of an event's participant
    """
    id = None
    seats = 0

    def __init__(self, **fields):
        self.__dict__.update(fields)

    def to_dict(self):
        """
        Returns self's data as a dictionary

        :return: dictionary with 'username', 'start' location information and, if 'driver' or not
        :rtype dict
        """
        if not self.driver:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': False}
        else:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': True, u'seats': self.seats}
        return dictionary

    def is_driver(self):
        """
        Is the participant listed as a driver

        :return: True if driver, False if not
        :rtype bool
        """
        if self.driver:
            return True
        else:
            return False

    def set_id(self, id):
        """
        Set the unique identifier of the participant

        :param id: the unique identifier
        :type id: str
        :return: None
        """
        self.id = id

    def set_seats(self, seats):
        """
        Set how many seats the participant's car has

        :param seats: total seats in the car
        :type seats: int
        :return: None
        """
        self.seats = seats

    def get_seats(self):
        """
        Return how many seats the participant's car has

        :return: The number of seats in the car
        :rtype int
        """
        return int(self.seats)


#######################
class Event(object):
    """
    This class is used as a local representation of an event in the database
    """
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
