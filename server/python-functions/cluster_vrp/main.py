from __future__ import print_function
from __future__ import division
import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions
from ortools.constraint_solver import routing_enums_pb2
from ortools.constraint_solver import pywrapcp
import requests
import json
import urllib.request

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
API_key = 'AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE'
gmaps = googlemaps.Client(key=API_key)

event_ref = None
destination = None

participants = {}


###########################
def cluster_vrp(request):
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

    destination = (event.destination.get(u'LatLng').latitude, event.destination.get(u'LatLng').longitude)
    for participant in event_participants:
        p = Participant(**participant.to_dict())
        p.set_id(participant.id)
        participants[p.id] = p
        if p.is_driver():
            cluster[p.id] = []

    print()
    print('Minimizing distance with COVRP.')
    print()
    group_cells_distance(cluster)
    distance_distance = calculate_cluster_distance(cluster)
    if event_optimization:
        # call waypoint optimization method TODO
        pass
    
    print('------------------------------')
    print('Final Cluster: {}'.format(cluster))
    print('Final Distance: {}'.format(distance_distance))
    update_database(cluster)
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


#########################
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
    cluster_vrp(return_dict)
