import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
import itertools
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions
from ortools.linear_solver import pywraplp
from math import radians, cos, sin, asin, sqrt


cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

event_ref = None
destination = None

participants = {}
participants_directions = {}

def cluster_lip(request):
    """
        Calculates the best possible cluster.

        The participants are split in riders and drivers for computations.
        Then the participants are matched between themselves:
            A cluster is calculated to minimize cars using a bin-packing algorithm
            A cluster is calculated to minimize total distance using Integer Linear Programming
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
    global participants, participants_directions
    global destination

    drivers = []
    drivers_id = []
    drivers_distance = ValueSortedDict()

    riders = []
    riders_id = []
    riders_distance = ValueSortedDict()

    rider_to_driver_route_share = {}

    participants = {}
    participants_directions = {}

    cluster = {}

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
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
            drivers.append(p)
            drivers_id.append(p.id)
        else:
            riders.append(p)
            riders_id.append(p.id)
        participants[p.id] = p

    print('Minimizing distance with ILP.')
    distance_distance, cluster_distance = group_cells_distance_euclidean(drivers_id, riders_id)
    # distance_distance, cluster_distance = group_cells_distance_euclidean(drivers_id, riders_id)
    # distance_distance, cluster_distance = group_cells_distance_euclidean(drivers_id, riders_id)
    distance_cluster = calculate_cluster_distance(cluster_distance)

    if event_optimization:
        # call waypoint optimization method TODO
        pass

    print('------------------------------')
    print('Final Cluster: {}'.format(cluster_distance))
    print('Final Distance: {}'.format(distance_cluster))
    update_database(cluster_distance)
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)

#########################
def group_cells_distance_real(drivers, riders):
    global participants

    # ILP solver
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

    number_drivers = len(drivers)
    cluster_participants = drivers + riders
    number_participants = len(cluster_participants)
    waypoints = cluster_participants + ["destination"]
    number_waypoints = number_participants + 1
    # How many seats does each car have
    drivers_seats = []
    for driver in drivers:
        drivers_seats.append(participants.get(driver).get_seats())
    # Matrix to store distances between every participant (number_participants x (number_participants + destination))
    participant_distance_matrix = {}
    for participant in cluster_participants:
        # Populated with 0's
        participant_distance_matrix[participant] = dict(zip(waypoints, [0] * number_waypoints))
    # Set matrix values to proper distances
    matrix_diagonal = 1
    for i in range(number_participants):
        participant1 = participants.get(cluster_participants[i])
        participant1_start = participant1.start.get(u'LatLng')
        participant1_source = (participant1_start.latitude, participant1_start.longitude)
        for j in range(matrix_diagonal, number_participants):
            participant2 = participants.get(cluster_participants[j])
            participant2_start = participant2.start.get(u'LatLng')
            participant2_source = (participant2_start.latitude, participant2_start.longitude)
            # Calculate distance from Participant 1 to Participant 2
            distance = gmaps.directions(participant1_source, participant2_source)[0].get(u'legs')[0].get(u'distance').get(u'value')
            participant_distance_matrix[cluster_participants[i]][cluster_participants[j]] = distance
            # The euclidean distance i->j is the same as j->i
            participant_distance_matrix[cluster_participants[j]][cluster_participants[i]] = distance
        participant_distance_matrix[cluster_participants[i]]["destination"] = gmaps.directions(participant1_source, destination)[0].get(u'legs')[0].get(u'distance').get(u'value')
        matrix_diagonal += 1

    # All possible combinations S of 1 driver and up to x riders, where x = empty seats.
    possible_combinations = []
    # Distances travelled in every driver's combinations
    distances = []
    # Map for each combination
    x = {}
    comb_len = 0
    for index in range(number_drivers):
        # Remove self from calculations
        remaining_participants = [x for i, x in enumerate(cluster_participants) if i != index]
        possible_combinations.append([])
        # For each driver get every possible combination of passengers (which includes riders and drivers)
        # From 0 passengers (only the driver) to a full car
        for seats in range(0, drivers_seats[index]+1):
            combination = [tuple([drivers[index]]+list(tup)) for tup in permutations(remaining_participants, seats)]
            possible_combinations[index] += combination
            comb_len += len(combination)
        distances.append([])
        number_combinations = len(possible_combinations[index])
        for j in range(0, number_combinations):
            # The total distance of the shortest route travelled by the combination
            distance = calculate_matrix_distance(possible_combinations[index][j], participant_distance_matrix)
            distances[index].append(distance)
            # Binary integer variables for the problem (0 or 1)
            # 1 means the combination was chose
            x[index, j] = solver.BoolVar('x[%i,%i]' % (index, j))

    # OBJECTIVE
    # Minimize the total distance covered
    solver.Minimize(solver.Sum([distances[i][j] * x[i, j] for i in range(len(drivers))
                                for j in range(len(possible_combinations[i]))]))

    # CONSTRAINTS
    # Each participant is selected exactly once
    for participant in participants:
        solver.Add(solver.Sum([x[i, j] for i in range(len(drivers)) for j in range(len(possible_combinations[i])) if participant in possible_combinations[i][j]]) == 1)

    # SOLVE
    sol = solver.Solve()

    cluster_distance = {}
    total_cost = solver.Objective().Value()
    print('Total cost = ', total_cost)
    print()
    for i in range(number_drivers):
        number_combinations = len(possible_combinations[i])
        for j in range(number_combinations):
            if x[i, j].solution_value() > 0:
                print('Combination {}. Cost {}'.format(possible_combinations[i][j], distances[i][j]))
                cluster_distance[possible_combinations[i][j][0]] = list(possible_combinations[i][j][1:])

    print()
    print("Time = ", solver.WallTime(), " milliseconds")
    return total_cost, cluster_distance


#########################
def group_cells_distance_haversine(drivers, riders):
    global participants, destination

    # ILP solver
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

    number_drivers = len(drivers)
    cluster_participants = drivers + riders
    number_participants = len(cluster_participants)
    waypoints = cluster_participants + ["destination"]
    number_waypoints = number_participants + 1
    # How many seats does each car have
    drivers_seats = []
    for driver in drivers:
        drivers_seats.append(participants.get(driver).get_seats())
    # Matrix to store distances between every participant (number_participants x (number_participants + destination))
    participant_distance_matrix = {}
    for participant in cluster_participants:
        # Populated with 0's
        participant_distance_matrix[participant] = dict(zip(waypoints, [0] * number_waypoints))
    # Set matrix values to proper distances
    matrix_diagonal = 1
    for i in range(number_participants):
        participant1 = participants.get(cluster_participants[i])
        participant1_start = participant1.start.get(u'LatLng')
        for j in range(matrix_diagonal, number_participants):
            participant2 = participants.get(cluster_participants[j])
            participant2_start = participant2.start.get(u'LatLng')
            # Calculate distance from Participant 1 to Participant 2
            distance = haversine_formula(participant1_start.latitude,
                                         participant1_start.longitude,
                                         participant2_start.latitude,
                                         participant2_start.longitude)
            participant_distance_matrix[cluster_participants[i]][cluster_participants[j]] = distance
            # The euclidean distance i->j is the same as j->i
            participant_distance_matrix[cluster_participants[j]][cluster_participants[i]] = distance
        participant_distance_matrix[cluster_participants[i]]["destination"] = haversine_formula(participant1_start.latitude,
                                                                                                participant1_start.longitude,
                                                                                                destination[0],
                                                                                                destination[1])
        matrix_diagonal += 1

    # All possible combinations S of 1 driver and up to x riders, where x = empty seats.
    possible_combinations = []
    # Distances travelled in every driver's combinations
    distances = []
    # Map for each combination
    x = {}
    comb_len = 0
    for index in range(number_drivers):
        # Remove self from calculations
        remaining_participants = [x for i, x in enumerate(cluster_participants) if i != index]
        possible_combinations.append([])
        # For each driver get every possible combination of passengers (which includes riders and drivers)
        # From 0 passengers (only the driver) to a full car
        for seats in range(0, drivers_seats[index]+1):
            permutation = [tuple([drivers[index]]+list(tup)) for tup in permutations(remaining_participants, seats)]
            possible_combinations[index] += permutation
            comb_len += len(permutation)
        distances.append([])
        number_combinations = len(possible_combinations[index])
        for j in range(0, number_combinations):
            # The total distance of the shortest route travelled by the combination
            distance = calculate_matrix_distance(possible_combinations[index][j], participant_distance_matrix)
            distances[index].append(distance)
            # Binary integer variables for the problem (0 or 1)
            # 1 means the combination was chose
            x[index, j] = solver.BoolVar('x[%i,%i]' % (index, j))

    # OBJECTIVE
    # Minimize the total distance covered
    solver.Minimize(solver.Sum([distances[i][j] * x[i, j] for i in range(len(drivers))
                                for j in range(len(possible_combinations[i]))]))

    # CONSTRAINTS
    # Each participant is selected exactly once
    for participant in participants:
        solver.Add(solver.Sum([x[i, j] for i in range(len(drivers)) for j in range(len(possible_combinations[i])) if participant in possible_combinations[i][j]]) == 1)

    # SOLVE
    sol = solver.Solve()

    cluster_distance = {}
    total_cost = solver.Objective().Value()
    print('Total cost = ', total_cost)
    print()
    for i in range(number_drivers):
        number_combinations = len(possible_combinations[i])
        for j in range(number_combinations):
            if x[i, j].solution_value() > 0:
                print('Combination {}. Cost {}'.format(possible_combinations[i][j], distances[i][j]))
                cluster_distance[possible_combinations[i][j][0]] = list(possible_combinations[i][j][1:])

    print()
    print("Time = ", solver.WallTime(), " milliseconds")
    return total_cost, cluster_distance


#########################
def group_cells_distance_euclidean(drivers, riders):
    global participants, destination

    # ILP solver
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

    number_drivers = len(drivers)
    cluster_participants = drivers + riders
    number_participants = len(cluster_participants)
    waypoints = cluster_participants + ["destination"]
    number_waypoints = number_participants + 1
    # How many seats does each car have
    drivers_seats = []
    for driver in drivers:
        drivers_seats.append(participants.get(driver).get_seats())
    # Matrix to store distances between every participant (number_participants x (number_participants + destination))
    participant_distance_matrix = {}
    for participant in cluster_participants:
        # Populated with 0's
        participant_distance_matrix[participant] = dict(zip(waypoints, [0]*number_waypoints))
    # Set matrix values to proper distances
    matrix_diagonal = 1
    for i in range(number_participants):
        participant1 = participants.get(cluster_participants[i])
        participant1_start = participant1.start.get(u'LatLng')
        for j in range(matrix_diagonal, number_participants):
            participant2 = participants.get(cluster_participants[j])
            participant2_start = participant2.start.get(u'LatLng')
            # Calculate distance from Participant 1 to Participant 2
            distance = euclidean_formula(participant1_start.latitude,
                                         participant1_start.longitude,
                                         participant2_start.latitude,
                                         participant2_start.longitude)
            participant_distance_matrix[cluster_participants[i]][cluster_participants[j]] = distance
            # The euclidean distance i->j is the same as j->i
            participant_distance_matrix[cluster_participants[j]][cluster_participants[i]] = distance
        participant_distance_matrix[cluster_participants[i]]["destination"] = euclidean_formula(participant1_start.latitude,
                                                                                                participant1_start.longitude,
                                                                                                destination[0],
                                                                                                destination[1])
        matrix_diagonal += 1

    # All possible combinations S of 1 driver and up to x riders, where x = empty seats.
    possible_combinations = []
    # Distances travelled in every driver's combinations
    distances = []
    # Map for each combination
    x = {}
    comb_len = 0
    for index in range(number_drivers):
        # Remove self from calculations
        remaining_participants = [x for i, x in enumerate(cluster_participants) if i != index]
        possible_combinations.append([])
        # For each driver get every possible combination of passengers (which includes riders and drivers)
        # From 0 passengers (only the driver) to a full car
        for seats in range(0, drivers_seats[index]+1):
            permutation = [tuple([drivers[index]]+list(tup)) for tup in permutations(remaining_participants, seats)]
            possible_combinations[index] += permutation
            comb_len += len(permutation)
        distances.append([])
        number_combinations = len(possible_combinations[index])
        for j in range(0, number_combinations):
            # The total distance of the shortest route travelled by the combination
            distance = calculate_matrix_distance(possible_combinations[index][j], participant_distance_matrix)
            distances[index].append(distance)
            # Binary integer variables for the problem (0 or 1)
            # 1 means the combination was chose
            x[index, j] = solver.BoolVar('x[%i,%i]' % (index, j))

    # OBJECTIVE
    # Minimize the total distance covered
    solver.Minimize(solver.Sum([distances[i][j] * x[i, j] for i in range(len(drivers))
                                for j in range(len(possible_combinations[i]))]))

    # CONSTRAINTS
    # Each participant is selected exactly once
    for participant in participants:
        solver.Add(solver.Sum([x[i, j] for i in range(len(drivers)) for j in range(len(possible_combinations[i])) if participant in possible_combinations[i][j]]) == 1)

    # SOLVE
    sol = solver.Solve()

    cluster_distance = {}
    total_cost = solver.Objective().Value()
    print('Total cost = ', total_cost)
    print()
    for i in range(number_drivers):
        number_combinations = len(possible_combinations[i])
        for j in range(number_combinations):
            if x[i, j].solution_value() > 0:
                print('Combination {}. Cost {}'.format(possible_combinations[i][j], distances[i][j]))
                cluster_distance[possible_combinations[i][j][0]] = list(possible_combinations[i][j][1:])

    print()
    print("Time = ", solver.WallTime(), " milliseconds")
    return total_cost, cluster_distance


#########################
def calculate_matrix_distance(combination, participant_distance_matrix):
    """
    Calculates the distance to travel to every element of the combination and then the destination

    :param combination: The participants of the route (driver + riders)
    :param participant_distance_matrix: A matrix of distances between each pair of participants.
                                        And from each participant to the destination.
    :return: The distance travelled by the combination
    """

    combination_size = len(combination)
    distance = 0
    for participant in range(combination_size - 1):
        distance += participant_distance_matrix[combination[participant]][combination[participant + 1]]
    distance += participant_distance_matrix[combination[-1]]["destination"]
    return distance


#########################
def combinations(items, howmany):
    return list(itertools.combinations(items, min(howmany, len(items))))


#########################
def permutations(items, howmany):
    return list(itertools.permutations(items, min(howmany, len(items))))


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
    :param lat1: Latitude of the second point
    :param lon1: Longitude of the second point
    :return: The distance between point1 an point2
    """

    return sqrt(((lat1-lat2)**2) + ((lon1-lon2)**2))


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
    for driver in cluster.keys():
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
        total_distance += distance
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
    cluster_lip(return_dict)
