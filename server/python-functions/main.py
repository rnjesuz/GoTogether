
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

participants = []

cluster = {}


######################
def main():
    global drivers, driversDistance, driversDirections, riders, ridersDistance, ridersDirections
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
            #driversDistance[p.id] = distance_results
            #driversDistance.__setitem__(p.id, distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value'))
            driversDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            driversDirections[p.id] = direction_results
            cluster[p.id] = []
        else:
            riders.append(p)
            #ridersDistance[p.id] = distance_results
            ridersDistance[p.id] = distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')
            ridersDirections[p.id] = direction_results
        participants.append(p)

        #print('source: '+source)
        #print(distance_results)
        #print(destination + ' to ' + source + ': ' + str(distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value')))

    #driversDistance = sorted(driversDistance.items(), key=lambda item: (item[1], item[0]))
    #ridersDistance = sorted(ridersDistance.items(), key=lambda item: (item[1], item[0]))

    for rider, rdistance in ridersDistance.items():
        for driver, ddistance in driversDistance.items():
            RtoDRouteShare[rider] = {}
            RtoDRouteShare[rider][driver] = getSharedPath(rider, driver)

    groupBestMatch()
    print(cluster)
    groupCells()
    #return cluster


##########################
def getSharedPath(rider, driver):
    share = 0
    rDirections = ridersDirections.get(rider)[0].get('legs')[0].get('steps')
    dDirections = driversDirections.get(driver)[0].get('legs')[0].get('steps')
    ridersDirections_range = len(rDirections)
    driversDirections_range = len(dDirections)
    for rsteps in range(ridersDirections_range):
        for dsteps in range(driversDirections_range):
            if (rDirections[rsteps].get('start_location') == dDirections[dsteps].get('start_location')) and (rDirections[rsteps].get('end_location') == dDirections[dsteps].get('end_location')):
                share = share+1
    return share


######################
def groupBestMatch():
    for rider in RtoDRouteShare:
        best_route = 0
        best_match = None
        match = False
        while match == False:
            for driver in RtoDRouteShare[rider]:
                if RtoDRouteShare[rider][driver] > best_route:
                    best_match = driver
            if best_match.seats <= len(cluster[best_match]): # TODO do i have to define len outide if?
                cluster[best_match].append(rider)
                match = True
            else:
                del RtoDRouteShare[rider][driver]


#########################
def groupCells():
    pass


######################
# Merge Sort
def mergeSort(alist):
    print("Splitting ",alist)
    if len(alist)>1:
        mid = len(alist)//2
        lefthalf = alist[:mid]
        righthalf = alist[mid:]

        mergeSort(lefthalf)
        mergeSort(righthalf)

        i=0
        j=0
        k=0
        while i < len(lefthalf) and j < len(righthalf):
            if lefthalf[i] < righthalf[j]:
                alist[k]=lefthalf[i]
                i=i+1
            else:
                alist[k]=righthalf[j]
                j=j+1
            k=k+1

        while i < len(lefthalf):
            alist[k]=lefthalf[i]
            i=i+1
            k=k+1

        while j < len(righthalf):
            alist[k]=righthalf[j]
            j=j+1
            k=k+1
    print("Merging ",alist)


#######################
class Participant(object):

    id = None;

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
        self.id = id;


#######################
class Event(object):
    def __init__(self, **fields):
        self.__dict__.update(fields)


if __name__ == '__main__':
    main()