import firebase_admin, google.cloud, googlemaps, json
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions

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
waypoints_latlng = {}

cluster = {}


######################
def cluster_route(request):
    global event_ref
    global drivers, driversDistance, driversDirections
    global riders, ridersDistance, ridersDirections
    global participants, RtoDRouteShare, cluster, waypoints_latlng

    drivers = []
    driversDirections = {}
    driversDistance = ValueSortedDict()

    riders = []
    ridersDirections = {}
    ridersDistance = ValueSortedDict()

    RtoDRouteShare = {}

    participants = {}
    participantsStart = {}

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
        direction_results = gmaps.directions(source, destination)  # TODO this may probably also return ZERO_RESULTS
        if p.is_driver():
            drivers.append(p)
            driversDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            ridersDirections[p.id] = direction_results
        participants[p.id] = p
        waypoints_latlng[p.id] = [p.start.get(u'LatLng').latitude, p.start.get(u'LatLng').longitude]
    waypoints_latlng[u'destination'] = [event.destination.get(u'LatLng').latitude,
                                        event.destination.get(u'LatLng').longitude]

    for rider in ridersDistance.keys():
        RtoDRouteShare[rider] = {}
        for driver in driversDistance.keys():
            RtoDRouteShare[rider][driver] = get_shared_path(rider, driver)

    print("Routes: {}".format(RtoDRouteShare))
    group_best_match()
    print("Route cluster: {}".format(cluster))
    group_cells()
    print(u'Created cluster: {}'.format(cluster))
    for driver in cluster.keys():
        reorganize_cells(driver, event.destination.get(u'LatLng'))
    # update_database()
    # return cluster
    # return 'OK'
    # data = {'response': 'OK'}
    # return json.dumps(data)


#########################
def reorganize_cells(driver, destination):
    graph = {}

    # create a graph between driver + riders
    graph[driver] = cluster.get(driver).copy()
    pickup_list = cluster.get(driver)
    for i,rider in enumerate(pickup_list):
        edges = pickup_list.copy()
        del edges[i]
        graph[rider] = edges

    # get minimum number of paths to traverse all edges of the graph
    untravelled_graph = graph.copy()
    travel_paths = []
    while bool(untravelled_graph):
        path = []
        path.append(driver)
        current_rider = untravelled_graph[driver][0]
        del untravelled_graph[driver][0]
        if not untravelled_graph[driver]:
            untravelled_graph.pop(driver, None)
        path.append(current_rider)
        has_untravelled_edges = True
        while has_untravelled_edges:
            if current_rider not in untravelled_graph:
                path.append(u'destination')
                has_untravelled_edges = False
            else:
                path.append(untravelled_graph[current_rider][0])
                next_rider = untravelled_graph[current_rider][0]
                del untravelled_graph[current_rider][0]
                if not untravelled_graph[current_rider]:
                    untravelled_graph.pop(current_rider, None)
                current_rider = next_rider
        travel_paths.append(path)
    print(u'travel paths: {}'.format(travel_paths))

    # apply weight to the graph
    weighted_graph = {}
    for path in travel_paths:
        waypoints = []
        for waypoint in path:
            if waypoint == u'destination':
                continue
            else:
                waypoints.append([participants.get(waypoint).start.get(u'LatLng').latitude, participants.get(waypoint).start.get(u'LatLng').longitude])
        print(u'lat: {}'.format(participants.get(path[0]).start.get(u'LatLng').latitude))
        print(u'long: {}'.format(participants.get(path[0]).start.get(u'LatLng').longitude))
        direction_results = gmaps.directions([participants.get(path[0]).start.get(u'LatLng').latitude, participants.get(path[0]).start.get(u'LatLng').longitude], [destination.latitude, destination.longitude], waypoints=waypoints)  # TODO this may probably also return ZERO_RESULTS
        print(waypoints_latlng)
        print(waypoints)
        for leg in direction_results[0].get(u'legs'):
            latlngStart = [leg.get(u'start_location').get(u'lat'), leg.get(u'start_location').get(u'lng')]
            print(latlngStart)
            start_node = list(waypoints_latlng.keys())[list(waypoints_latlng.values()).index(latlngStart)]
            print(u'start: {}'.format(start_node))
            latlngEnd = [leg.get(u'end_location').get(u'lat'), leg.get(u'end_location').get(u'lng')]
            end_node = list(waypoints_latlng.keys())[list(waypoints_latlng.values()).index(latlngEnd)]
            print(u'end: {}'.format(end_node))
            weighted_graph[start_node] = {end_node : leg.get(u'distance').get(u'value')}

    print(weighted_graph)
    # apply a search algorithm in a weighted graph