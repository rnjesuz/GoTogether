
import firebase_admin, google.cloud, googlemaps, operator, sortedcontainers, sortedcollections
from sortedcontainers import SortedDict
from sortedcollections import ValueSortedDict
from firebase_admin import credentials, firestore
from google.cloud import exceptions

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

drivers = []
driversDirections = {}
driversDistance = ValueSortedDict()

riders = []
ridersDirections = {}
ridersDistance = ValueSortedDict()

RtoDRouteShare = {}

participants = {}

cluster = {}


######################
def main():
    global drivers, driversDistance, driversDirections, riders, ridersDistance, ridersDirections, participants, RtoDRouteShare, cluster
    # eventRef = db.collection(u'events').document(u'reference.eventUID')
    eventRef = db.collection(u'events').document(u'SBgh4MKtplFEbYXLvmMY')
    # participantsRef = db.collection(u'events').document(reference.eventUID).collection(u'participants')
    participantsRef = db.collection(u'events').document(u'SBgh4MKtplFEbYXLvmMY').collection(u'participants')
    try:
        event = Event(**eventRef.get().to_dict())
        eventParticipants = participantsRef.get()
    except google.cloud.exceptions.NotFound:
        print(u'No such document')

    destination = event.destination.get(u'street')
    #print('destination: '+destination)
    for participant in eventParticipants:
        p = Participant(**participant.to_dict())
        p.setId(participant.id)
        source = p.start.get(u'street')
        distance_results = gmaps.distance_matrix(source, destination) # TODO this can result ZERO_RESULTS
        direction_results = gmaps.directions(source, destination) # TODO this may probably also return ZERO_RESULTS
        if p.isDriver():
            drivers.append(p)
            # driversDistance[p.id] = distance_results
            # driversDistance.__setitem__(p.id, distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value'))
            driversDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            #ridersDistance[p.id] = distance_results
            ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            ridersDirections[p.id] = direction_results
        participants[p.id] = p

        #print('source: '+source)
        #print(distance_results)
        #print(destination + ' to ' + source + ': ' + str(distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')))

    #driversDistance = sorted(driversDistance.items(), key=lambda item: (item[1], item[0]))
    #ridersDistance = sorted(ridersDistance.items(), key=lambda item: (item[1], item[0]))

    for rider in ridersDistance.keys():
        for driver in driversDistance.keys():
            RtoDRouteShare[rider] = {}
            RtoDRouteShare[rider][driver] = getSharedPath(rider, driver)

    groupBestMatch()
    groupCells()
    print(cluster)
    #return cluster


##########################
def getSharedPath(rider, driver):
    share = 0
    r_directions = ridersDirections.get(rider)[0].get('legs')[0].get('steps')
    d_directions = driversDirections.get(driver)[0].get('legs')[0].get('steps')
    riders_directions_range = len(r_directions)
    drivers_directions_range = len(d_directions)
    for rsteps in range(riders_directions_range):
        for dsteps in range(drivers_directions_range):
            if (r_directions[rsteps].get('start_location') == d_directions[dsteps].get('start_location')) and (r_directions[rsteps].get('end_location') == d_directions[dsteps].get('end_location')):
                share = share+1
    return share


######################
def groupBestMatch():
    global participants

    for rider in RtoDRouteShare:
        match = False
        while not match:
            best_route = 0
            best_match = None
            # TODO if no driver left in list then return exception
            for driver in RtoDRouteShare[rider]:
                # TODO use a percentage calculation? best_route / nº nodes
                if RtoDRouteShare[rider][driver] > best_route:
                    best_match = driver
            if cluster.get(best_match):
                cluster_length = len(cluster.get(best_match))
            else:
                cluster_length = 0
            participant = participants.get(best_match)
            seats = participant.getSeats()
            if seats >= cluster_length:
                cluster[best_match].append(rider)
                match = True
            else:
                del RtoDRouteShare[rider][driver]


#########################
def groupCells():
    # TODO try to match full cars (2 empty + 2, instead of 2 empty +1)
    for driver in driversDistance.keys():
        for next_driver in driversDistance.keys():
            if next_driver == driver:
                continue
            else:

                pass
        pass

    pass


#######################
class Participant(object):

    id = None
    seats = 0

    def __init__(self, **fields):
        self.__dict__.update(fields)

    #def __init__(self, username, start, seats):
    #    self.username = username
    #    self.start = start
    #    if seats == -1:
    #        self.driver = False
    #    else:
    #        self.driver = True
    #        self.seats = seats

    def to_dict(self):
        if not self.driver:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': False}
        else:
            dictionary = {u'username': self.username, u'start': self.start, u'driver': True, u'seats': self.seats}
        return dictionary

    def isDriver(self):
        if self.driver:
            return True
        else:
            return False

    def setId(self, id):
        self.id = id

    def setSeats(self, seats):
        self.seats = seats

    def getSeats(self):
        return self.seats


#######################
class Event(object):
    def __init__(self, **fields):
        self.__dict__.update(fields)


if __name__ == '__main__':
    main()