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
    cluster_riders = {}
    for participant in event_participants:
        p = Participant(**participant.to_dict())
        p.set_id(participant.id)
        source = (p.start.get(u'LatLng').latitude, p.start.get('LatLng').longitude)
        # distance_results = gmaps.distance_matrix(source, destination)  # TODO this can result ZERO_RESULTS
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            drivers_id.append(p.id)
            # driversDistance[p.id]= distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            drivers_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            riders_id.append(p.id)
            # ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            riders_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # ridersDirections[p.id] = direction_results
            cluster_riders[p.id] = []
        participants[p.id] = p
        participants_directions[p.id] = direction_results

    for rider in riders_distance.keys():
        rider_to_driver_route_share[rider] = {}
        for driver in drivers_distance.keys():
            rider_to_driver_route_share[rider][driver] = get_shared_path(rider, driver)

    print("Shared Nodes: {}".format(rider_to_driver_route_share))
    group_best_match_riders(cluster, rider_to_driver_route_share)
    print("Route clusters: {}".format(cluster))

    print('------------------------------')
    print('Calculating INITIAL values.')
    initial_cars = len(cluster)
    print('Initial number of  cars: ' + str(initial_cars) + '.')
    initial_cluster = cluster.copy()
    initial_cluster.update(cluster_riders)
    initial_distance = calculate_cluster_distance(initial_cluster)
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
    distance_distance, cluster_distance = group_cells_distance_real(drivers_id, riders_id)
    # distance_distance = calculate_cluster_distance(cluster_distance)
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


###########################
def get_shared_path(first_participant, second_participant):
    """
    Calculates value of shared route between two participants (from their locations till the destination)

    Get the route between the first participant till the destination. We get the set of steps the participant traverses
    Get the route between the second participant till the destination. We get the set of steps the participant traverses
    Compare routes, and calculate number of identical steps
    Divide by the total number of steps from the first participant
        (The idea is to see how much of the total 1st participant's route, is shared by the 2nd participant)

    :param first_participant: The unique identifier for the first participant
    :type first_participant: str
    :param second_participant: The unique identifier for the second participant
    :type second_participant: str
    :return: The value of shared route
    :rtype: float
    """
    share = 0
    f_directions = participants_directions.get(first_participant)[0].get('legs')[0].get('steps')
    s_directions = participants_directions.get(second_participant)[0].get('legs')[0].get('steps')
    # TODO can this be done with "contains"?
    for r_steps in range(len(f_directions)):
        for d_steps in range(len(s_directions)):
            if (f_directions[r_steps].get('start_location') == s_directions[d_steps].get('start_location')) and \
                    (f_directions[r_steps].get('end_location') == s_directions[d_steps].get('end_location')):
                share = share + 1
    # The optimal group will be the one whose route has the biggest percentage of route share
    # Thus, we return shared_nodes / len(nodes_of_my_route)
    # print('who {}, to who {}, what {}'.format(first_participant, second_participant, share/len(s_directions)))
    return share / len(f_directions)


##########################
def group_best_match_riders(cluster, rider_to_driver_route_share):
    """
    Matches riders with drivers based on the heuristic
    The 'cluster' is updated directly with the new matches

    :param cluster: The cluster matching riders to drivers
    :type cluster: dict
    :param rider_to_driver_route_share: the set with values of how much route is shared between riders and drivers
    :type rider_to_driver_route_share: dict
    :return: None
    """
    global participants
    for rider in rider_to_driver_route_share:
        match = False
        while not match:
            best_route = 0
            best_match = None
            for driver in rider_to_driver_route_share[rider]:
                if rider_to_driver_route_share[rider][driver] > best_route:
                    best_route = rider_to_driver_route_share[rider][driver]
                    best_match = driver
            if best_match in cluster:
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = 0
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if seats > cluster_length:  # if they're <= the new rider won't fit
                cluster[best_match].append(rider)
                match = True
            else:
                del rider_to_driver_route_share[rider][best_match]
                if not rider_to_driver_route_share[rider]:  # is empty
                    match = True


#########################
def group_cells_distance_real(drivers, riders):
    global participants

    # ILP solver
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

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
    for index in range(len(drivers)):
        # Remove self from calculations
        remaining_participants = [x for i, x in enumerate(cluster_participants) if i != index]
        possible_combinations.append([])
        # For each driver get every possible combination of passengers (which includes riders and drivers)
        # From 0 passengers (only the driver) to a full car
        for seats in range(0, drivers_seats[index]+1):
            combination = [tuple([drivers[index]]+list(tup)) for tup in combinations(remaining_participants, seats)]
            possible_combinations[index] += combination
            comb_len += len(combination)
        distances.append([])
        for j in range(0, len(possible_combinations[index])):
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
    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
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
    for index in range(len(drivers)):
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
        for j in range(0, len(possible_combinations[index])):
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
    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
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
    for index in range(len(drivers)):
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
        for j in range(0, len(possible_combinations[index])):
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
    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
            if x[i, j].solution_value() > 0:
                print('Combination {}. Cost {}'.format(possible_combinations[i][j], distances[i][j]))
                cluster_distance[possible_combinations[i][j][0]] = list(possible_combinations[i][j][1:])

    print()
    print("Time = ", solver.WallTime(), " milliseconds")
    return total_cost, cluster_distance


#########################
def combinations(items, howmany):
    return list(itertools.combinations(items, min(howmany, len(items))))


#########################
def permutations(items, howmany):
    return list(itertools.permutations(items, min(howmany, len(items))))


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
def calculate_combination_distance_real(combination):
    """
    Calculates the distance travveled by the participants of the provided combination

    The combination is a tuple
        The first element is the driver
        The remaining elements are riders
    Riders pick-up order is internally optimized by Google's API
    The distance is calculated by:
        starting in the driver's location,
        picking up the riders and
        ending at the destination

    :param combination: the collection with the driver and the riders
    :type combination: tuple
    :return: the distance travelled
    :rtype: int
    """
    global participants, destination
    distance = 0
    waypoints = []
    for rider in combination[1:]:
        waypoint_geopoint = participants.get(rider).start.get(u'LatLng')
        waypoints.append((waypoint_geopoint.latitude, waypoint_geopoint.longitude))
    origin_geopoint = participants.get(combination[0]).start.get(u'LatLng')
    origin = (origin_geopoint.latitude, origin_geopoint.longitude)
    direction_results = gmaps.directions(origin, destination,
                                         mode="driving",
                                         waypoints=waypoints,
                                         units="metric",
                                         optimize_waypoints=True)
    # if there are waypoints then there are several legs to consider
    for i in range(len(waypoints)+1):
        distance += direction_results[0].get(u'legs')[i].get(u'distance').get(u'value')
    return distance


#########################
def calculate_combination_distance_haversine(combination):
    global participants, destination
    distance = 0
    for i in range(0,len(combination)):
        participant1_id = combination[i]
        participant1 = participants.get(participant1_id)
        if i+1 == len(combination):
            distance += haversine_formula(participant1.start.get(u'LatLng').latitude,
                                          participant1.start.get(u'LatLng').longitude,
                                          destination[0],
                                          destination[1])
        else:
            participant2_id = combination[i+1]
            participant2 = participants.get(participant2_id)
            distance += haversine_formula(participant1.start.get(u'LatLng').latitude,
                                          participant1.start.get(u'LatLng').longitude,
                                          participant2.start.get(u'LatLng').latitude,
                                          participant2.start.get(u'LatLng').longitude)
    return distance


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
def calculate_combination_distance_euclidean(combination):
    global participants, destination
    distance = 0
    for i in range(0, len(combination)):
        participant1_id = combination[i]
        participant1 = participants.get(participant1_id)
        # If last rider then calculate distance to destination
        if i + 1 == len(combination):
            distance += euclidean_formula(participant1.start.get(u'LatLng').latitude,
                                          participant1.start.get(u'LatLng').longitude,
                                          destination[0],
                                          destination[1])
        # Else calculate from current participant to next one in the combination
        else:
            participant2_id = combination[i + 1]
            participant2 = participants.get(participant2_id)
            distance += euclidean_formula(participant1.start.get(u'LatLng').latitude,
                                          participant1.start.get(u'LatLng').longitude,
                                          participant2.start.get(u'LatLng').latitude,
                                          participant2.start.get(u'LatLng').longitude)
    return distance


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


#########################
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
    # get shared route between driver and possible matches
    for match in driver_passengers_tuple:
        if driver == match[0]:
            continue
        driver_passengers_tuple[index] = (*driver_passengers_tuple[index], get_shared_path(driver, match[0]))
        index += 1
    # Order the tuple
    #     The key = lambda x: (x[1], x[2]) should be read as:
    #     "firstly order by the seats in x[1] and then by the shared route value in x[2]".
    driver_passengers_tuple = sorted(driver_passengers_tuple, key=lambda x: (x[1], x[2]))
    return driver_passengers_tuple


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
