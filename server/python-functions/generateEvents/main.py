import firebase_admin
import googlemaps
import random
from firebase_admin import credentials
from firebase_admin import firestore
from random import randint

gmaps = googlemaps.Client(key='AIzaSyDSyIfDZVr7DMspukdJG00gzZUnPCCqguE')

# Use a service account
cred = credentials.Certificate('ServiceAccountKey.json')
firebase_admin.initialize_app(cred)

db = firestore.client()


def generateEvents():
    destination_latitude = 38.722252
    destination_longitude = -9.139337
    number_of_participants = int(input("Quantas pessoas? "))
    event_name = input("Qual e o nome do evento? ")
    event_destination = "Lisboa, Portugal"
    # event_destination = input("Qual e o destino? ")
    minX = 39.213133
    # minX = int(input("Min X? "))
    maxX = 39.676679
    # maxX = int(input("Max X? "))
    minY =  -8.830733
    # minY = int(input("Min Y? "))
    maxY = -8.390575
    # maxY = int(input("Max Y? "))

    # Create event on db
    eventRef = db.collection("events").document(event_name)
    data = {}
    data["cluster"] = None
    data["completed"] = False
    destinationMap = {}
    destinationMap["LatLng"] =  firestore.GeoPoint(destination_latitude, destination_longitude)
    destinationMap["street"] = event_destination
    data["destination"] = destinationMap
    data["image"] = "ic_launcher_round"
    data["owner"] = "jUxXtyL864QpbByHI9JUHDs1oay1"
    data["title"] = event_name

    drivers = []
    for i in range(0, number_of_participants):
        participant_name = "Participant" + str(i)

        # add participant to database
        participantRef = db.collection("users").document(participant_name)
        p_data = {}
        p_data["username"] = participant_name
        p_data["events"] = [eventRef]
        participantRef.set(data)


        participantInDocRef = eventRef.collection("participants").document(participant_name)
        participant_latitude = getRandomNumber(minX, maxX)
        participant_longitude = getRandomNumber(minY, maxY)

        p_data = {}
        if getRandomBoolean():
            drivers.append(participantRef)
            p_data["driver"] = True
            p_data["seats"] = getRandomInt(1, 7)
            p_data["username"] = participant_name
            participantMap = {}
            participantMap["LatLng"] = firestore.GeoPoint(participant_latitude, participant_longitude)
            participantMap["street"] = streetFromLatlng(participant_latitude, participant_longitude)
            p_data["start"] = participantMap
            # add participant to event
            participantInDocRef.set(p_data)
        else:
            p_data["driver"] = False
            p_data["username"] = participant_name
            participantMap = {}
            participantMap["LatLng"] = firestore.GeoPoint(participant_latitude, participant_longitude)
            participantMap["street"] = streetFromLatlng(participant_latitude, participant_longitude)
            p_data["start"] = participantMap
            # add participant to event
            participantInDocRef.set(p_data)

    data["participants"] = number_of_participants
    data["drivers"] = drivers
    eventRef.set(data)

def getRandomNumber(min, max):
    return random.uniform(min, max)

def getRandomInt(min, max):
    return randint(min, max)

def getRandomBoolean():
    return bool(random.getrandbits(1))

def streetFromLatlng(latitude, longitude):
    return gmaps.reverse_geocode((latitude, longitude))[0][u'formatted_address']



if __name__ == '__main__':
    generateEvents()