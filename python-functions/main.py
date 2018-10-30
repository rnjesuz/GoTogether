
import firebase_admin, google.cloud, googlemaps, operator
from firebase_admin import credentials, firestore
from google.cloud import exceptions

cred = credentials.Certificate("./ServiceAccountKey.json")
default_app = firebase_admin.initialize_app(cred)
db = firestore.client()
gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

drivers = []
driversDirections = {}
driversDistance = {}

riders = []
ridersDirections = {}
ridersDistance = {}
participants = []


######################
def main(driversDistance=None):
    # eventRef = db.collection(u'events').document(u'reference.eventUID')
    eventRef = db.collection(u'events').document(u'SxMNyI4fqaMjNko7OXF9')
    # participantsRef = db.collection(u'events').document(reference.eventUID).collection(u'participants')
    participantsRef = db.collection(u'events').document(u'SxMNyI4fqaMjNko7OXF9').collection(u'participants')
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
            driversDistance[p.id] = distance_results
            driversDirections[p.id] = direction_results
        else:
            riders.append(p)
            ridersDistance[p.id] = distance_results
            ridersDirections[p.id] = direction_results
        participants.append(p)

        #print('source: '+source)
        #print(distance_results)
        #print(destination + ' to ' + source + ': ' + distance_results.get(u'rows')[0].get(u'elements')[0].get(u'distance').get(u'value'))

    driversDistance = sorted(driversDistance.items(), key=lambda kv: kv[1])
    ridersDistance = sorted(ridersDistance.items(), key=lambda kv: kv[1])


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