import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
import urllib.request
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions
from ortools.constraint_solver import routing_enums_pb2
from ortools.constraint_solver import pywrapcp

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
API_key = 'AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE'
gmaps = googlemaps.Client(key=API_key)

event_ref = None
destination = None

participants = {}
participants_directions = {}


###########################
def cluster(request):
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
    global participants, participants_directions
    global destination

    drivers = []
    drivers_distance = ValueSortedDict()

    riders = []
    riders_distance = ValueSortedDict()

    rider_to_driver_route_share = {}

    participants = {}
    participants_directions = {}

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
        source = (p.start.get(u'LatLng').latitude, p.start.get('LatLng').longitude)
        # distance_results = gmaps.distance_matrix(source, destination)  # TODO this can result ZERO_RESULTS
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            # driversDistance[p.id]= distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            drivers_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            # ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            riders_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            # ridersDirections[p.id] = direction_results
        participants[p.id] = p
        participants_directions[p.id] = direction_results

    cluster_distance = copy.deepcopy(cluster)

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
    group_cells_distance(cluster_distance)
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


######################
def group_cells_distance(cluster_distance):
    """Solve the COVRP problem."""

    # Instantiate the data problem.
    data = create_data()

    # Create the routing index manager.
    manager = pywrapcp.RoutingIndexManager(
        len(data['distance_matrix']), data['num_vehicles'], data['starts'], data['ends'])
    # Create Routing Model.
    routing = pywrapcp.RoutingModel(manager)

    # Create and register a transit callback.
    def distance_callback(from_index, to_index):
        """Returns the distance between the two nodes."""
        # Convert from routing variable Index to distance matrix NodeIndex.
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return data['distance_matrix'][from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(distance_callback)

    # Define cost of each arc.
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    # Add Capacity constraint.
    def demand_callback(from_index):
        """Returns the demand of the node."""
        # Convert from routing variable Index to demands NodeIndex.
        from_node = manager.IndexToNode(from_index)
        return data['demands'][from_node]

    demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)
    routing.AddDimensionWithVehicleCapacity(
        demand_callback_index,
        0,  # null capacity slack
        data['vehicle_capacities'],  # vehicle maximum capacities
        True,  # start cumul to zero
        'Capacity')

    # Setting first solution heuristic.
    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = (
        routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC)

    # Solve the problem.
    solution = routing.SolveWithParameters(search_parameters)

    # Print solution on console.
    if solution:
        read_solution(data, manager, routing, solution, cluster_distance)


def create_data():
    """Creates the data."""
    global participants

    # Addresses of the participants
    addresses = []
    # Seats occupied by each participant (1)
    demands = []
    # Seats available in each car
    capacities = []
    # Start location of each driver
    starts = []
    # The dictionary to store data
    data = {}
    data['API_key'] = API_key
    # destination goes first (index 0)
    addresses.append(destination)
    demands.append(0)
    number_drivers = 0
    drivers_addresses = []
    drivers_demands = []
    participants_id = []
    drivers_id = []
    for index, participant_id in enumerate(participants):
        participant = participants.get(participant_id)
        participant_start = participant.start.get('LatLng')
        addresses.append((participant_start.latitude, participant_start.longitude))
        participants_id.append(participant_id)
        if participant.is_driver():
            drivers_addresses.append((participant_start.latitude, participant_start.longitude))
            drivers_demands.append(1)
            drivers_id.append(participant_id)
            demands.append(0)
            starts.append(index+1)
            capacities.append(participant.get_seats()+1)
            number_drivers += 1
        else:
            demands.append(1)
    addresses += drivers_addresses
    demands += drivers_demands
    participants_id += drivers_id
    data['starts'] = starts
    # Ids of the participants
    data['ids'] = participants_id
    # End location of each driver
    data['ends'] = [0]*number_drivers  # They all end on 'destination'
    data['addresses'] = addresses
    data['demands'] = demands
    data['vehicle_capacities'] = capacities
    data['num_vehicles'] = number_drivers
    distance_matrix = create_distance_matrix(data)
    distance_matrix[0] = [0]*len(addresses)
    data['distance_matrix'] = distance_matrix
    '''print('ids: {}'.format(participants_id))
    print('addresses: {}'.format(addresses))
    print('distance_matrix: {}'.format(distance_matrix))
    print('demands : {}'.format(demands))
    print('vehicle_capacities : {}'.format(capacities))
    print('num_vehicles : {}'.format(number_drivers))
    print('starts: {}'.format(starts))
    print('ends: {}'.format([0]*number_drivers))'''
    return data


def create_distance_matrix(data):
    addresses = data["addresses"]
    API_key = data["API_key"]
    # Distance Matrix API only accepts 100 elements per request, so get rows in multiple requests.
    max_elements = 100
    num_addresses = len(addresses)
    # Maximum number of rows that can be computed per request.
    max_rows = max_elements // num_addresses
    # num_addresses = q * max_rows + r.
    q, r = divmod(num_addresses, max_rows)
    dest_addresses = addresses
    distance_matrix = []
    # Send q requests, returning max_rows rows per request.
    for i in range(q):
        origin_addresses = addresses[i * max_rows: (i + 1) * max_rows]
        response = send_request(origin_addresses, dest_addresses, API_key)
        distance_matrix += build_distance_matrix(response)

    # Get the remaining r rows, if necessary.
    if r > 0:
        origin_addresses = addresses[q * max_rows: q * max_rows + r]
        response = send_request(origin_addresses, dest_addresses, API_key)
        distance_matrix += build_distance_matrix(response)

    return distance_matrix


def send_request(origin_addresses, dest_addresses, API_key):
    """ Build and send request for the given origin and destination addresses."""

    def build_address_str(addresses):
        # Build a pipe-separated string of addresses
        address_str = ''
        for i in range(len(addresses) - 1):
            address_str += '' + str(addresses[i][0]) + ',' + str(addresses[i][1]) + '|'
        address_str += str(addresses[-1][0]) + ',' + str(addresses[-1][1])
        return address_str

    request = 'https://maps.googleapis.com/maps/api/distancematrix/json?units=metric'
    origin_address_str = build_address_str(origin_addresses)
    dest_address_str = build_address_str(dest_addresses)
    request = request + '&origins=' + origin_address_str + '&destinations=' + \
              dest_address_str + '&key=' + API_key
    jsonResult = urllib.request.urlopen(request).read()
    response = json.loads(jsonResult)
    return response


def build_distance_matrix(response):
    distance_matrix = []
    for row in response['rows']:
        row_list = [row['elements'][j]['distance']['value'] for j in range(len(row['elements']))]
        distance_matrix.append(row_list)
    return distance_matrix


def read_solution(data, manager, routing, assignment, cluster_distance):
    """Prints assignment on console."""
    total_distance = 0
    total_load = 0
    # get IDs of every participant
    ids = data['ids']
    for vehicle_id in range(data['num_vehicles']):
        index = routing.Start(vehicle_id)
        plan_output = 'Route for vehicle {}:\n'.format(vehicle_id)
        route_distance = 0
        route_load = 0
        riders = []
        # Get driver
        driver_index = manager.IndexToNode(index)
        driver_id = ids[driver_index-1]
        while not routing.IsEnd(index):
            node_index = manager.IndexToNode(index)
            route_load += data['demands'][node_index]
            # plan_output += ' {0} Load({1}) -> '.format(node_index, route_load)
            plan_output += ' {0} Load({1}) -> '.format(ids[node_index-1], route_load)
            previous_index = index
            index = assignment.Value(routing.NextVar(index))
            route_distance += routing.GetArcCostForVehicle(previous_index, index, vehicle_id)
            # If it's a rider
            if (ids[node_index-1]) != driver_id:
                riders.append(ids[node_index-1])
        # plan_output += ' {0} Load({1})\n'.format(manager.IndexToNode(index), route_load)
        plan_output += ' {0} Load({1})\n'.format('Destination', route_load)
        plan_output += 'Distance of the route: {}m\n'.format(route_distance)
        plan_output += 'Load of the route: {}\n'.format(route_load)
        print(plan_output)
        total_distance += route_distance
        total_load += route_load

        # Build the cluster
        if route_distance == 0:
            del cluster_distance[driver_id]
        elif route_distance > 0:
            # Add riders to driver cluster
            cluster_distance[driver_id] += riders
    print('Total distance of all routes: {}m'.format(total_distance))
    print('Total load of all routes: {}'.format(total_load))


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
    # get shared route between driver and possible matches
    print("antes da heuristica: {}".format(driver_passengers_tuple))
    for match in driver_passengers_tuple:
        if driver == match[0]:
            continue
        driver_passengers_tuple[index] = (*driver_passengers_tuple[index], get_shared_path(driver, match[0]))
        index += 1
    print("depois da heuristica: {}".format(driver_passengers_tuple))
    # Order the tuple
    #     The key = lambda x: (x[1], x[2]) should be read as:
    #     "firstly order by the seats in x[1] and then by the shared route value in x[2]".
    driver_passengers_tuple = sorted(driver_passengers_tuple, key=lambda x: (x[1], x[2]))
    print("depois da ordenaçao: {}".format(driver_passengers_tuple))
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
    cluster(return_dict)
