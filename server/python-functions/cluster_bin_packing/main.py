import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from math import sqrt
from google.cloud import exceptions

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

event_ref = None
destination = None
group_method = None

participants = {}
participants_directions = {}


###########################
def cluster_bin_packing(request):
    """
    Calculates the best possible cluster minimizing cars, using a bin packing methodology

    The algorithm splits participants in riders and drivers
    Then clustering is done based on the chosen group method:
        - Bin packing
        - Heuristics:   - Route shared
                        - Radius
                        - Voronoi cells
    If bin packing is chosen:
        The algorithm runs using riders and drivers simultaneously while grouping
    If an heuristic was chosen:
        The riders are matched with the best available driver based on the heuristic
        Then, cars are matched between themselves using the bin packing method, to reduce vehicles
    The database is updated with the final cluster

    :param request: a formatted request with values for the algorithm
        eventUID (String): The name of the event, from which to calculate the cluster
        cars (float): How much weight given to minimizing number of cars. Cars = 100 - distance
        distance (float): How much weight given to minimizing total distance traveled. Distance = 100 - cars
        group method: (String): The chosen clustering method
        optimization (boolean): Optimize the order of the riders in each car? *TODO*
    :type request: JSON

    :return: None

    TODOs
        Complete implementation of module using 'optimization' parameter
    """
    global event_ref, group_method
    global participants, participants_directions
    global destination

    drivers = []
    drivers_id = []
    drivers_distance = ValueSortedDict()

    riders = []
    riders_id = []
    riders_distance = ValueSortedDict()

    rider_to_driver_route_share = {}
    rider_to_driver_distance = {}

    participants = {}
    participants_directions = {}

    cluster = {}

    # request_json = request.get_json()
    # event_uid = request_json['eventUID']
    # if event_uid is None:
    # 	event_uid = u'SBgh4MKtplFEbYXLvmMY'
    event_uid = request['eventUID']
    group_method = request['group method']
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
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            drivers_id.append(p.id)
            drivers_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
            cluster[p.id] = []
        else:
            riders.append(p)
            riders_id.append(p.id)
            riders_distance[p.id] = direction_results[0].get(u'legs')[0].get(u'distance').get(u'value')
        participants[p.id] = p
        participants_directions[p.id] = direction_results

    # Function that groups riders with drivers using the bin packing algorithm
    def group_bin_packing():
        print('Minimizing cars using BIN PACKING.')
        group_cluster_bin_packing(cluster, riders_id)
        return calculate_cluster_distance(cluster)

    # Function that groups riders with drivers using an heuristic based on route shared
    def group_route():
        print('Grouping riders with drivers using SHARED ROUTES')
        for rider in riders_distance.keys():
            rider_to_driver_route_share[rider] = {}
            for driver in drivers_distance.keys():
                rider_to_driver_route_share[rider][driver] = get_shared_path(rider, driver)
        print("Shared Nodes: {}".format(rider_to_driver_route_share))
        group_best_match_riders(cluster, rider_to_driver_route_share)
        print("Route clusters: {}".format(cluster))
        print('------------------------------')
        print('Minimizing cars using BIN PACKING.')
        group_cells_cars(cluster)
        return calculate_cluster_distance(cluster)

    # Function that groups riders with drivers using an heuristic based on voronoi cells
    def group_voronoi_cells():
        print('Grouping riders with drivers using VORONOI CELLS')
        for rider in riders_id:
            source_lat_lng = participants.get(rider).start.get(u'LatLng')
            source = (source_lat_lng.latitude, source_lat_lng.longitude)
            rider_to_driver_distance[rider] = {}
            for driver in drivers_id:
                destination_lat_lng = participants.get(driver).start.get(u'LatLng')
                destination = (destination_lat_lng.latitude, destination_lat_lng.longitude)
                rider_to_driver_distance[rider][driver] = gmaps.distance_matrix(source, destination).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS

        print("Distances: {}".format(rider_to_driver_distance))
        group_best_match_riders(cluster, rider_to_driver_distance)
        print('Voronoi cluster: {}'.format(cluster))
        print('------------------------------')
        print('Minimizing cars using BIN PACKING.')
        group_cells_cars(cluster)
        return calculate_cluster_distance(cluster)

    # Function that groups riders with drivers using an heuristic based on radius
    def group_radius():
        print('Grouping riders with drivers by RADIUS')
        radius_step = 5  # Kilometers
        possible_riders = riders_id.copy()
        for rider in possible_riders:
            rider_to_driver_radius = calculate_radius(radius_step, rider, drivers_id)
            for driver, radius in rider_to_driver_radius.items():
                seats = participants.get(driver).get_seats()
                if seats > len(cluster.get(driver)):
                    print(u'Group! Rider \'{}\' grouped with driver \'{}\'.'.format(rider, driver))
                    print(u'At radius: {}'.format(radius))
                    cluster[driver].append(rider)
                    break
        print(u'Radial clusters: {}'.format(cluster))
        print('------------------------------')
        print('Minimizing cars using BIN PACKING.')
        group_cells_cars(cluster)
        return calculate_cluster_distance(cluster)

    # Default response used when the grouping method is invalid
    def group_invalid():
        print('Invalid method of grouping')
        exit(0)

    # Dictionary of function names
    switcher = {
        'bin packing': group_bin_packing,
        'route': group_route,
        'voronoi': group_voronoi_cells,
        'radius': group_radius
    }

    # Get the grouping method from the switcher dictionary, and execute it
    distance = switcher.get(group_method, group_invalid)()

    if event_optimization:
        # call waypoint optimization method TODO
        pass

    print('------------------------------')
    print('Final Cluster: {}'.format(cluster))
    print('Final Distance: {}'.format(distance))
    update_database(cluster)


######################
def calculate_radius(radius_step, rider_uid, drivers):
    """
    Calculates the radius interval between the rider and the drivers

    :param radius_step: The incremental value for each radius interval
    :type radius_step: int
    :param rider_uid: The unique identifier of the pivot
    :type rider_uid: str
    :param drivers: List of participants for calculations
    :type drivers: list
    :return: Dictionary with the interval from the rider to the other participants
    :rtype: dict
    """
    rider_geopoint = participants.get(rider_uid).start.get(u'LatLng')
    rider_to_driver_radius = ValueSortedDict()
    for driver in drivers:
        driver_geopoint = participants.get(driver).start.get(u'LatLng')
        # Get the euclidean formula between driver and match
        distance = euclidean_formula(driver_geopoint.latitude, driver_geopoint.longitude,
                                     rider_geopoint.latitude, rider_geopoint.longitude)
        # Round up to the next multiple of 'radius'. Multiples of 'radius' stay the same
        radius_distance = ((distance+(radius_step-1))//radius_step)*radius_step
        # Store the values
        rider_to_driver_radius[driver] = radius_distance
    return rider_to_driver_radius


def euclidean_formula(lat1, lon1, lat2, lon2):
    """
    Calculates the Euclidean (direct) distance between two points

    :param lat1: Latitude of the first point
    :type lat1: float
    :param lon1: Longitude of the first point
    :type lon1: float
    :param lat2: Latitude of the second point
    :type lat2: float
    :param lon2: Longitude of the second point
    :type lon2: float
    :return: The distance between point1 an point2
    :rtype: float
    """

    return sqrt(((lat1-lat2)**2) + ((lon1-lon2)**2))


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


def group_best_match_riders(cluster, rider_to_driver_heuristic):
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
    for rider in rider_to_driver_heuristic:
        match = False
        while not match:
            best_value = 0
            best_match = None
            for driver in rider_to_driver_heuristic[rider]:
                if rider_to_driver_heuristic[rider][driver] >= best_value:  # accept one even if all values are 0
                    best_value = rider_to_driver_heuristic[rider][driver]
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
                del rider_to_driver_heuristic[rider][best_match]
                if not rider_to_driver_heuristic[rider]:  # is empty
                    match = True


#########################
def group_cluster_bin_packing(cluster_cars, riders):
    """
    Applies a bin packing algorithm to match riders with drivers while minimizing the number of bins (cars)

    :param cluster_cars: The collection of cars
    :param riders: The participants who are riders
    :return: The computed cluster
    """
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
        print('New driver: ', new_driver)
        del copy_driver_passengers[new_driver]
        driver_passengers_tuple = [(k, v) for k, v in copy_driver_passengers.items()]
        driver_passengers_tuple += [(rider, 1) for rider in riders]
        #    Order the cars based on the heuristic
        driver_passengers_tuple = order_by_heuristic(new_driver, driver_passengers_tuple)
        print('Possible passengers: '.format(driver_passengers_tuple))
        #    Calculate the bin packing solution
        available_seats = list(driver_seats.values())[-1]
        if (not driver_passengers_tuple) == False:
            bins = binpacking.to_constant_volume(driver_passengers_tuple, available_seats, 1, -1, available_seats + 1)
        else:
            del driver_seats[new_driver]
            continue
        print('Created bins: {}'.format(bins))
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
        print('Possible best bin(s): {}'.format(possible_best_bin))
        bins_with_more_passengers = [u for u, v in possible_best_bin.items() if
                                     int(v) >= max(possible_best_bin.values())]
        print('Bin(s) with more passengers: {}'.format(bins_with_more_passengers))
        best_bins = [bins[x] for x in bins_with_more_passengers]
        print('Best bin(s): {}'.format(best_bins))

        # If multiple solutions exist...
        if len(best_bins) > 1:
            # ... compare them by distance...
            bin_distances = []
            for bin in best_bins:
                cluster_bins = {new_driver: []}
                for participant in bin:
                    cluster_bins[new_driver].append(participant[0])  # picking up the driver
                    if participants.get(participant[0]).is_driver():
                        cluster_bins[new_driver] += cluster_cars[participant[0]]  # and it's passenger
                bin_distances.append(calculate_cluster_distance(cluster_bins))
            # ... and pick the one with the smallest distance (if tied between several - choose any)
            better_bin = tuple(best_bins)[bin_distances.index(min(bin_distances))]
        else:
            better_bin = next(iter(best_bins))
        print('Final bin: {}'.format(better_bin))
        print_cluster = []
        for i in range(0, better_bin.__len__()):
            driver_of_bin = better_bin[i][0]
            if participants.get(driver_of_bin).is_driver():
                print_cluster += [driver_of_bin] + cluster_cars[driver_of_bin]
            else:
                print_cluster += [driver_of_bin]
        print("Best bin for {}: {}".format(new_driver, print_cluster))

        # Remove the grouped cars (the bin + the items) from the sorted list and from the unplaced items.
        for driver in better_bin:
            if participants.get(driver[0]).is_driver():
                del driver_seats[driver[0]]
                del driver_passengers[driver[0]]
        # Remove the receiving driver from the sorted list and from the unplaced items.
        del driver_seats[new_driver]
        del driver_passengers[new_driver]
        # Update cluster. The biggest bin as passengers of the driver with more empty seats
        for old_driver in better_bin:  # join cars
            cluster_cars[new_driver].append(old_driver[0])
            if participants.get(old_driver[0]).is_driver():
                for rider in cluster_cars.get(old_driver[0]):
                    cluster_cars[new_driver].append(rider)
                # remove old car from available clusters
                del cluster_cars[old_driver[0]]
            else:
                riders.remove(old_driver[0])
        # Repeat the bin packing algorithm with the next emptiest car and with the remaining passenger groups,
        # until no more packing is possible.

    print("Calculated cluster: {}".format(cluster_cars))
    return cluster_cars


#########################
def group_cells_cars(cluster_cars):
    """
    Applies a bin packing algorithm to reduce number of bins (cars)

    :param cluster_cars: The cluster to minimize
    :return: The new minimized cluster
    """
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
        print('New driver: ', new_driver)
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
            try:
                # The driver might've been removed earlier for being an unsuitable driver
                del driver_seats[driver[0]]
            except KeyError:
                pass
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


def order_by_heuristic(driver, driver_passengers_tuple):
    """
    Orders the set after calculating values based on the chosen method (if it was an heuristic)

    Calculates the heuristic value between the driver and each element of the set
    Adds each calculated value to the last position of each element of the set
    Orders the set based on its stored parameter first and then the heuristic value

    :param driver: the unique identifier of the driver
    :type driver: str
    :param driver_passengers_tuple: a list of tuples containing several participants and their seats
    :return: the new set, ordered, with the new heuristic values
    :rtype list of tuples
    """
    if group_method != 'bin packing':  # bin packing method doesn't have another ordering value
        index = 0
        # get shared route between driver and possible matches
        for match in driver_passengers_tuple:
            if driver == match[0]:
                continue
            if group_method == 'route':
                driver_passengers_tuple[index] = (*driver_passengers_tuple[index], get_shared_path(driver, match[0]))

            elif group_method == 'voronoi':
                source = participants.get(driver).start.get(u'street')
                destination = participants.get(match[0]).start.get(u'street')
                distance_result = gmaps.distance_matrix(source, destination).get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')  # TODO this can result ZERO_RESULTS
                driver_passengers_tuple[index] = (*driver_passengers_tuple[index], distance_result)

            elif group_method == 'radius':
                radius = 10  # Kilometers
                participant_geopoint = participants.get(driver).start.get(u'LatLng')
                match_geopoint = participants.get(match[0]).start.get(u'LatLng')
                # Get the euclidean formula between participant and match
                distance = euclidean_formula(participant_geopoint.latitude, participant_geopoint.longitude,
                                             match_geopoint.latitude, match_geopoint.longitude)
                # Round up to the next multiple of 'radius'. Multiples of 'radius' stay the same
                radius_distance = ((distance + (radius - 1)) // radius) * radius
                # Create new tuple with 'radius_distance' in the last position
                driver_passengers_tuple[index] = (*driver_passengers_tuple[index], radius_distance)
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
    cluster_bin_packing(return_dict)
