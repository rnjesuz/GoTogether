import firebase_admin
import google.cloud
import googlemaps
import json
import binpacking
import copy
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
participants_directions = {}


######################
def cluster_route(request):
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

    for rider in riders_distance.keys():
        rider_to_driver_route_share[rider] = {}
        for driver in drivers_distance.keys():
            rider_to_driver_route_share[rider][driver] = get_shared_path(rider, driver)

    print("Shared Nodes: {}".format(rider_to_driver_route_share))
    group_best_match_riders(cluster, rider_to_driver_route_share)
    print("Route clusters: {}".format(cluster))

    print('------------------------------')
    print('Calculating INITIAL values.')
    initial_distance = calculate_cluster_distance(cluster)
    print('Total initial distance: ' + str(initial_distance))
    initial_cars = len(cluster)

    # ----------------------------------
    #
    # f(x)=(cars_parameter*(len(x)/initial_cars))+(distance_parameter*(calculate_cluster_distance(x)/initial_distance))
    #
    print('------------------------------')
    print('Calculating cluster minimizing CARS.')
    cluster_cars = group_cells_cars(copy.deepcopy(cluster))
    distance_cars = calculate_cluster_distance(cluster_cars)
    f_cluster_cars = (cars_parameter * (len(cluster_cars) / initial_cars)) +\
                     (distance_parameter * (distance_cars / initial_distance))
    print('------------------------------')
    print('Calculating cluster minimizing DISTANCE.')
    cluster_distance = group_cells_distance(copy.deepcopy(cluster), drivers_distance)
    distance_distance = calculate_cluster_distance(cluster_distance)
    f_cluster_distance = (cars_parameter * (len(cluster_distance) / initial_cars)) +\
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
    share = 0
    f_directions = participants_directions.get(first_participant)[0].get('legs')[0].get('steps')
    s_directions = participants_directions.get(second_participant)[0].get('legs')[0].get('steps')
    for r_steps in range(len(f_directions)):
        for d_steps in range(len(s_directions)):
            if (f_directions[r_steps].get('start_location') == s_directions[d_steps].get('start_location')) and\
                    (f_directions[r_steps].get('end_location') == s_directions[d_steps].get('end_location')):
                share = share+1
    # The optimal group will be the one whose route has the biggest percentage of route share
    # Thus, we return shared_nodes / len(nodes_of_possible_group)
    print('who {}, to who {}, what {}'.format(first_participant, second_participant, share/len(s_directions)))
    return share/len(s_directions)


##########################
def group_best_match_riders(cluster, rider_to_driver_route_share):
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
def group_cells_distance(cluster_distance, drivers_distance):
    # calculate route shared with other drivers, till the destination
    driver_to_driver_route_share = {}
    for driver in drivers_distance.keys():
        driver_to_driver_route_share[driver] = {}
        for other_driver in drivers_distance.keys():
            if driver is other_driver:
                continue
            driver_to_driver_route_share[driver][other_driver] = get_shared_path(driver, other_driver)
    # group cars with the best available option
    return group_best_match_drivers(cluster_distance, driver_to_driver_route_share)


######################
def group_best_match_drivers(cluster_distance, driver_to_driver_route_share):
    global participants

    # order cars by number of empty seats
    driver_seats = ValueSortedDict()
    for driver in driver_to_driver_route_share.keys():
        # get number of empty seats
        cluster_list = cluster_distance.get(driver)
        cluster_list_length = len(cluster_list)
        driver_seats[driver] = participants.get(driver).get_seats() - cluster_list_length
    print(driver_seats)

    # TODO optimize cycles
    # for driver in driver_to_driver_route_share:
    for driver in driver_seats:
        print('1: {}'.format(driver_seats))
        if driver not in cluster_distance:
            print('saltei')
            continue
        match = False
        while not match:
            best_route = 0
            best_match = None
            for new_driver in driver_to_driver_route_share[driver]:
                if driver_to_driver_route_share[driver][new_driver] > best_route:
                    best_route = driver_to_driver_route_share[driver][new_driver]
                    best_match = new_driver
            if best_match is None:
                return cluster_distance
            participant = participants.get(best_match)
            seats = participant.get_seats()
            if best_match in cluster_distance:
                cluster_length = len(cluster_distance.get(best_match))
            else:
                # TODO if it was removed... continue?
                cluster_length = seats
            print('Can - {} - pick-up - {}\'s - cluster?'.format(best_match, driver))
            if seats >= cluster_length + len(cluster_distance.get(driver)) + 1:
                print('A:    Yes')
                # see if the cumulative distance of the join is better than the separate voyages
                print('    Is the cumulative distance smaller Joined or Separate?')
                if verify_cumulative_distance(cluster_distance, best_match, driver):
                    # join cars
                    for rider in cluster_distance.get(driver):
                        cluster_distance[best_match].append(rider)
                    cluster_distance[best_match].append(driver)
                    # remove old car from available clusters
                    del cluster_distance[driver]
                    print('New cluster: {}'.format(cluster_distance))
                    match = True
                else:
                    del driver_to_driver_route_share[driver][best_match]
                    if not driver_to_driver_route_share[driver]:  # is empty
                        match = True
            else:
                print('A:    No. Not enough seats')
                del driver_to_driver_route_share[driver][best_match]
                if not driver_to_driver_route_share[driver]:  # is empty
                    match = True
            print('2: {}'.format(driver_seats))
    return cluster_distance


######################
def verify_cumulative_distance(cluster, new_driver, old_driver):
    print("        Separate distance:")
    old_distance_new_driver = calculate_cluster_distance({new_driver: cluster.get(new_driver)})
    old_distance_old_driver = calculate_cluster_distance({old_driver: cluster.get(old_driver)})
    old_distance = old_distance_old_driver + old_distance_new_driver
    print("        Total: " + str(old_distance))
    # TODO does the dictionary for the new_distance always old?
    print("        Joined distance:")
    new_distance = calculate_cluster_distance({new_driver: cluster.get(new_driver)+[old_driver]+cluster.get(old_driver)})
    if old_distance < new_distance:
        print('    A: Separate')
        return False
    else:
        print('    A: Joined')
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
        new_driver = list(driver_seats.keys())[-1]
        del copy_driver_passengers[new_driver]
        #    Calculate the bin packing solution
        bins = binpacking.to_constant_volume(copy_driver_passengers, list(driver_seats.values())[-1])
        print('Possible bins for {}: {}'.format(new_driver, bins))
        # Of the bins produced by the algorithm, choose the bin with more items in it.

        #             MOST EFFICIENT
        #
        #             max_length = 0
        #             result = dict()
        #             for i, d in enumerate(bins):
        #               l = len(d)
        #               if l == max_length:
        #                   result[i] = d
        #               elif l > max_length:
        #                   max_length = l
        #                   result = {i: d}
        #
        _max = max(map(len, bins))
        # TODO remove the dict from bellow. i only need the bin not it's position.
        # TODO (cont) i only need the position in the new array, not it's positionin the original array
        # TODO change the last line of the if statement (with the tuple conversion)
        # biggest_bins = dict(i for i in enumerate(bins) if len(i[-1]) == _max)



        result = {}
        count = 0
        for i in bins:
            sum = 0
            for j in i.values():
                sum += j
            result[count] = sum
            count += 1
        a = [u for u,v in result.items() if float(v) >= max(result.values())]
        biggest_bins = [bins[x] for x in a]
        # TODO os cíclos agr teem de ser diferentes

        print('bins: {}'.format(biggest_bins))
        # If multiple solutions exist...
        if len(biggest_bins) > 1:
            # ... compare them by distance...
            bin_distances = []
            for bin in biggest_bins.values():
                cluster_bins = {new_driver: []}
                for participant in bin.keys():
                    cluster_bins[new_driver].append(participant)
                bin_distances.append(calculate_cluster_distance(cluster_bins))
            print("distances; {}".format(bin_distances))
            # ... and pick the one with the smallest distance (if draw between several - choose any)
            better_bin = tuple(biggest_bins.items())[bin_distances.index(min(bin_distances))][1]
        else:
            better_bin = next(iter(biggest_bins.values()))
        # Remove the grouped cars (the bin + the items) from the sorted list and from the unplaced items.
        for driver in better_bin.keys():
            del driver_seats[driver]
            del driver_passengers[driver]
        # Remove the receiving driver from the sorted list and from the unplaced items.
        del driver_seats[new_driver]
        del driver_passengers[new_driver]
        # Update cluster. The biggest bin as passengers of the driver with more empty seats
        for old_driver in better_bin:  # join cars
            for rider in cluster_cars.get(old_driver):
                cluster_cars[new_driver].append(rider)
            cluster_cars[new_driver].append(old_driver)
            # remove old car from available clusters
            del cluster_cars[old_driver]
        print(cluster_cars)
        # Repeat the bin packing algorithm with the next emptiest car and with the remaining passenger groups,
        # until no more packing is possible.
        if not driver_passengers:  # evaluates to true when empty
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
                        # looking in the mirror
                        if participants.get(next_driver) == participants.get(driver):
                            reset = False
                            continue
                        # driver already has a full car
                        elif participants.get(next_driver).get_seats() <= cluster_list_length:
                            reset = False
                            continue
                        # driver has available seats
                        # #(riders)+driver <= (possible car).seats - already occupied seats
                        elif (len(cluster.get(driver)))+1 <= \
                                (participants.get(next_driver).get_seats()-cluster_list_length):
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
        print("        Travelled by: " + driver)
        waypoints = []
        for rider in cluster.get(driver):
            waypoint_geopoint = participants.get(rider).start.get(u'LatLng')
            waypoints.append((waypoint_geopoint.latitude, waypoint_geopoint.longitude))
        origin_geopoint = participants.get(driver).start.get(u'LatLng')
        origin = (origin_geopoint.latitude, origin_geopoint.longitude)
        print('            Starting in: {}, Picking-up in: {}, Arriving in: {}'.format(origin, waypoints, destination))
        direction_results = gmaps.directions(origin, destination,
                                             mode="driving",
                                             waypoints=waypoints,
                                             units="metric",
                                             optimize_waypoints=True)
        # if there are waypoints then there are several legs to consider
        distance = 0
        for i in range(len(waypoints)+1):
            distance += direction_results[0].get(u'legs')[i].get(u'distance').get(u'value')
        print("            Distance:" + str(distance))
        total_distance += distance
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
    cluster_route(return_dict)
