import firebase_admin
import google.cloud
import googlemaps
import json
import statistics as stats
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions
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

    riders = []

    participants = {}
    participants_id = []

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

    destination = (event.destination.get(u'LatLng').latitude, event.destination.get(u'LatLng').longitude)
    for participant in event_participants:
        p = Participant(**participant.to_dict())
        p.set_id(participant.id)
        if p.is_driver():
            drivers.append(p.id)
            cluster[p.id] = []
        else:
            riders.append(p.id)
        participants[p.id] = p
        participants_id.append(p.id)

    number_participants = len(participants)
    waypoints = list(participants.keys()) + ["destination"]
    number_waypoints = number_participants + 1
    # Matrix to store distances between every participant (number_participants x (number_participants + destination))
    participants_distance_matrix = {}
    for participant in participants:
        # Populated with 0's
        participants_distance_matrix[participant] = dict(zip(waypoints, [0] * number_waypoints))
    # Set matrix values to proper distances
    matrix_diagonal = 1
    for i in range(number_participants):
        participant1 = participants.get(participants_id[i])
        participant1_start = participant1.start.get(u'LatLng')
        for j in range(matrix_diagonal, number_participants):
            participant2 = participants.get(participants_id[j])
            participant2_start = participant2.start.get(u'LatLng')
            # Calculate distance from Participant 1 to Participant 2
            distance = euclidean_formula(participant1_start.latitude,
                                         participant1_start.longitude,
                                         participant2_start.latitude,
                                         participant2_start.longitude)
            participants_distance_matrix[participants_id[i]][participants_id[j]] = distance
            # The euclidean distance i->j is the same as j->i
            participants_distance_matrix[participants_id[j]][participants_id[i]] = distance
        participants_distance_matrix[participants_id[i]]["destination"] = euclidean_formula(participant1_start.latitude,
                                                                                           participant1_start.longitude,
                                                                                           destination[0],
                                                                                           destination[1])
        matrix_diagonal += 1

    radius_step = 5  # Kilometers
    possible_riders = riders.copy()
    for rider in possible_riders:
        rider_to_driver_radius = calculate_radius(radius_step, rider, drivers)
        for driver, radius in rider_to_driver_radius.items():
            seats = participants.get(driver).get_seats()
            if seats > len(cluster.get(driver)):
                print(u'Group! Rider \'{}\' grouped with driver \'{}\'.'.format(rider, driver))
                print(u'At radius: {}'.format(radius))
                cluster[driver].append(rider)
                break

    print(u'Radial clusters: {}'.format(cluster))

    print('------------------------------')
    print('Calculating cluster minimizing DISTANCE.')
    cluster_distance = group_cells_distance(cluster, drivers, participants_distance_matrix)
    distance_distance = calculate_cluster_distance(cluster_distance)

    if event_optimization:
        # call waypoint optimization method TODO
        pass
    print('------------------------------')
    print('Final Cluster: {}'.format(cluster_distance))
    print('Final Destination: {}'.format(distance_distance))
    update_database(cluster_distance)


######################
def calculate_radius(radius_step, rider_uid, drivers):
    rider_geopoint = participants.get(rider_uid).start.get(u'LatLng')
    rider_to_driver_radius = ValueSortedDict()
    for driver in drivers:
        driver_geopoint = participants.get(driver).start.get(u'LatLng')
        # Get the haversine formula between driver and match
        distance = euclidean_formula(driver_geopoint.latitude, driver_geopoint.longitude,
                                     rider_geopoint.latitude, rider_geopoint.longitude)
        # Round up to the next multiple of 'radius'. Multiples of 'radius' stay the same
        radius_distance = ((distance+(radius_step-1))//radius_step)*radius_step
        # Store the values
        rider_to_driver_radius[driver] = radius_distance
    return rider_to_driver_radius


######################
def haversine_formula(lat1, lon1, lat2, lon2):
    """
    Calculate the great circle distance between two points
    on the earth (specified in decimal degrees)
    :param lat1: latitude of the first point
    :param lon1: longitude of the first point
    :param lat2: latitude of the second point
    :param lon2: longitude of the second point
    :return: haversine distance between both points
    """

    # convert decimal degrees to radians
    lat1, lon1, lat2, lon2 = map(radians, (lat1, lon1, lat2, lon2))
    # haversine formula
    lon = lon2 - lon1
    lat = lat2 - lat1
    a = sin(lat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(lon / 2) ** 2
    c = 2 * asin(sqrt(a))
    r = 6371.008  # Radius of earth in kilometers. Use 3956 for miles
    return c * r


#########################
def euclidean_formula(lat1, lon1, lat2, lon2):
    """
    Calculates the Euclidean (direct) distance between two points
    :param lat1: Latitude of the first point
    :param lon1: Longitude of the first point
    :param lat2: Latitude of the second point
    :param lon2: Longitude of the second point
    :return: The distance between point1 an point2
    """

    return sqrt(((lat1-lat2)**2) + ((lon1-lon2)**2))


######################
def group_cells_distance(cluster_distance, drivers, participants_distance_matrix):
    radius_step = 10  # Kilometers
    remaining_drivers = drivers.copy()
    for driver in drivers:
        print('DRIVER: {}'.format(driver))
        if driver not in remaining_drivers:
            continue
        remaining_drivers_minus_driver = list(remaining_drivers)
        remaining_drivers_minus_driver.remove(driver)
        radius_distances = calculate_radius(radius_step, driver, remaining_drivers_minus_driver)
        for possible_driver, radius in radius_distances.items():
            print('POSSIBLE DRIVER: {}'.format(possible_driver))
            seats = participants.get(possible_driver).get_seats()
            if seats >= len(cluster_distance.get(possible_driver)) + len(cluster_distance.get(driver)) + 1:
                if verify_cumulative_distance(cluster_distance, possible_driver, driver, participants_distance_matrix):
                    print(u'Group! Driver \'{}\' grouped with new driver \'{}\'.'
                          .format(driver, possible_driver))
                    for rider in cluster_distance.get(driver) or []:
                        cluster_distance[possible_driver].append(rider)
                    cluster_distance[possible_driver].append(driver)
                    remaining_drivers.remove(driver)
                    del cluster_distance[driver]
                    break
                else:
                    print(u'NO Group! Roundtrip is longer.')
            else:
                print(u'NO Group! Insufficient seats.')
    return cluster_distance


######################
def verify_cumulative_distance(cluster, new_driver, old_driver, participants_distance_matrix):
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
    '''old_distance_new_driver = calculate_cluster_distance({new_driver: cluster.get(new_driver)})
    old_distance_old_driver = calculate_cluster_distance({old_driver: cluster.get(old_driver)})
    old_distance = old_distance_old_driver + old_distance_new_driver'''
    old_distance_new_driver = 0
    cluster_size = len(cluster.get(new_driver))
    new_driver_cluster = cluster.get(new_driver)
    for index in range(cluster_size + 1):
        if index == 0:
            old_distance_new_driver += participants_distance_matrix[new_driver][new_driver_cluster[0]]
        elif index == cluster_size:
            old_distance_new_driver += participants_distance_matrix[new_driver_cluster[-1]]['destination']
        else:
            old_distance_new_driver += participants_distance_matrix[new_driver_cluster[index - 1]][new_driver_cluster[index]]
    old_distance_old_driver = 0
    cluster_size = len(cluster.get(old_driver))
    old_driver_cluster = cluster.get(old_driver)
    for index in range(cluster_size+1):
        if index == 0:
            old_distance_old_driver += participants_distance_matrix[old_driver][old_driver_cluster[0]]
        elif index == cluster_size:
            old_distance_old_driver += participants_distance_matrix[old_driver_cluster[-1]]['destination']
        else:
            old_distance_old_driver += participants_distance_matrix[old_driver_cluster[index-1]][old_driver_cluster[index]]

    old_distance = old_distance_old_driver + old_distance_new_driver
    print("        Total: " + str(old_distance))

    # TODO does the dictionary for the new_distance always old?
    print("        Joined distance:")
    '''new_distance = calculate_cluster_distance(
        {new_driver: cluster.get(new_driver) + [old_driver] + cluster.get(old_driver)})'''
    new_distance = 0
    cluster_size = len(cluster.get(old_driver)) + 1 + len(cluster.get(new_driver))
    new_cluster = cluster.get(new_driver) + [old_driver] + cluster.get(old_driver)
    for index in range(cluster_size + 1):
        if index == 0:
            new_distance += participants_distance_matrix[new_driver][new_cluster[0]]
        elif index == cluster_size:
            new_distance += participants_distance_matrix[old_driver_cluster]['destination']
        else:
            new_distance += participants_distance_matrix[new_cluster[index - 1]][new_cluster[index]]
    print("        Total: " + str(new_distance))

    if old_distance < new_distance:
        print('    A: Separate')
        return False
    else:
        print('    A: Joined')
        return True


#########################
def calculate_cluster_distance(cluster):
    """
        Calculates the total travelled distance of the provided cluster

        For each driver of the cluster, we calculate the travelled distance
            We start the route at the driver's location, pick-up all the passengers and end the route at the destination
            Waypoint order is internally optimized by Google's API
        All distances are added together for total distance travelled

        :param cluster: the cluster matching riders to drivers
        :type cluster: dict
        :return: the total distance travelled by the each driver of the cluster
        :rtype int
        """
    global participants, destination
    total_distance = 0
    car_passengers = []
    car_distances = []
    for driver in cluster.keys():
        car_passengers.append(len(cluster.get(driver)))
        # print("        Travelled by: " + driver)
        waypoints = []
        for rider in cluster.get(driver):
            waypoint_geopoint = participants.get(rider).start.get(u'LatLng')
            waypoints.append((waypoint_geopoint.latitude, waypoint_geopoint.longitude))
        origin_geopoint = participants.get(driver).start.get(u'LatLng')
        origin = (origin_geopoint.latitude, origin_geopoint.longitude)
        # print('            Starting in: {}, Picking-up in: {}, Arriving in: {}'.format(origin, waypoints, destination))
        direction_results = gmaps.directions(origin, destination,
                                             mode="driving",
                                             waypoints=waypoints,
                                             units="metric",
                                             optimize_waypoints=True)
        # if there are waypoints then there are several legs to consider
        distance = 0
        for i in range(len(waypoints) + 1):
            distance += direction_results[0].get(u'legs')[i].get(u'distance').get(u'value')
        # print("            Distance:" + str(distance))
        car_distances.append(distance)
        total_distance += distance
    passengers_std_deviation = stats.pstdev(car_passengers)
    print('EVALUATION Number of cars: {}'.format(len(cluster)))
    print('EVALUATION Standard deviation of passengers \'{}\' is: {}'.format(car_passengers, passengers_std_deviation))
    distances_std_deviation = stats.pstdev(car_distances)
    print('EVALUATION Total Distance: {}'.format(total_distance))
    print('EVALUATION Standard deviation of distances \'{}\' is: {}'.format(car_distances, distances_std_deviation))
    return total_distance


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
