import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from random import randint
from math import ceil

# Use a service account
cred = credentials.Certificate('ServiceAccountKey.json')
firebase_admin.initialize_app(cred)

db = firestore.client()

def generateEvent():
    number_of_participants = [5, 7, 10, 12, 15]
    event_name = ["DFar PNear with 5", "DFar PNear with 7", "DFar PNear with 10", "DFar PNear with 12", "DFar PNear with 15"]
    event_destination = "Largo Sé Velha 33, 3000-383 Coimbra"
    destination_latitude, destination_longitude = (40.208834, -8.427373)
    coordinates = [(38.716407, -9.149407),
                   (38.710283, -9.144035),
                   (38.735357, -9.138818),
                   (38.767721, -9.097644),
                   (38.756704, -9.153974),
                   (38.741813, -9.168845),
                   (38.728389, -9.185444),
                   (38.713009, -9.159942),
                   (38.714656, -9.134281),
                   (38.707707, -9.173730),
                   (38.726143, -9.150101),
                   (38.738231, -9.155177),
                   (38.715795, -9.131066),
                   (38.723975, -9.161457),
                   (38.753870, -9.187482)]

    addresses = ["Praça do Príncipe Real 18-14, 1250-096 Lisboa",
                 "R. Horta Seca 11-1, 1200-213 Lisboa",
                 "Av. Rovisco Pais, 1000-287 Lisboa",
                 "Av. Dom João II, 1990-221 Lisboa",
                 "Campo Grande 18, 1700-162 Lisboa",
                 "Praça Marechal Humberto Delgado, Lisboa",
                 "Estr. da Bela Vista, Lisboa",
                 "R. João de Deus, 1200-694 Lisboa",
                 "Costa do Castelo 57, 1100-335 Lisboa",
                 "Av. Ceuta, Lisboa",
                 "Praça Marquês de Pombal, 1070-051 Lisboa",
                 "Av. de Berna 52-56, 1050-099 Lisboa",
                 "Largo Graça 204, 1100-266 Lisboa",
                 "Av. Eng. Duarte Pacheco, 1250-096 Lisboa",
                 "Av. Colégio Militar, 1500-392 Lisboa"]

    drivers_addresses = []
    drivers_coordinates = []
    riders_addresses = []
    riders_coordinates = []
    for event in range(5):
        print(event_name[event])
        # Create event on db
        eventRef = db.collection("events").document(event_name[event])
        data = {}
        data["cluster"] = None
        data["completed"] = False
        destinationMap = {}
        destinationMap["LatLng"] = firestore.GeoPoint(destination_latitude, destination_longitude)
        destinationMap["street"] = event_destination
        data["destination"] = destinationMap
        data["image"] = "ic_launcher_round"
        data["owner"] = "jUxXtyL864QpbByHI9JUHDs1oay1"
        data["title"] = event_name[event]

        drivers_number = ceil(number_of_participants[event]/2)
        drivers = []
        # Create drivers
        drivers_seats = 0
        possible_drivers_coordinates = []
        possible_drivers_addresses = []
        addresses_copy = addresses.copy()
        coordinates_copy = coordinates.copy()
        while drivers_seats < (number_of_participants[event] - drivers_number):
            addresses_copy = addresses.copy()
            coordinates_copy = coordinates.copy()
            drivers_seats = 0
            for i in range(drivers_number):
                print('drivers i: {}'.format(i))
                participant_name = "Participant" + str(i)
                # add participant to database
                participantRef = db.collection("users").document(participant_name)
                p_data = {}
                p_data["username"] = participant_name
                p_data["events"] = [eventRef]
                participantRef.update(p_data)

                p_data = {}
                p_data["driver"] = True
                seats = getRandomInt(1, 7)
                p_data["seats"] = seats
                p_data["username"] = participant_name
                participantInDocRef = eventRef.collection("participants").document(participant_name)
                if i < len(drivers_coordinates):
                    participant_latitude, participant_longitude = drivers_coordinates[i]
                    participantMap = {"LatLng": firestore.GeoPoint(participant_latitude, participant_longitude),
                                      "street": drivers_addresses[i]}
                else:
                    index_random = getRandomInt(0, len(coordinates_copy)-1)
                    print('drivers random: {}'.format(index_random))
                    participant_latitude, participant_longitude = coordinates_copy[index_random]
                    possible_drivers_coordinates.append(coordinates_copy[index_random])
                    del coordinates_copy[index_random]
                    participantMap = {"LatLng": firestore.GeoPoint(participant_latitude, participant_longitude),
                                      "street": addresses_copy[index_random]}
                    possible_drivers_addresses.append(addresses_copy[index_random])
                    del addresses_copy[index_random]
                drivers.append(participantRef)
                p_data["start"] = participantMap
                # add participant to event
                participantInDocRef.set(p_data)
                drivers_seats += seats

        print('old add: {}'.format(addresses))
        drivers_addresses += possible_drivers_addresses
        print('pos driv add: {}'.format(possible_drivers_addresses))
        addresses = addresses_copy
        print('new add: {}'.format(addresses))
        print()
        print('old coord: {}'.format(coordinates))
        drivers_coordinates += possible_drivers_coordinates
        print('pos driv coord: {}'.format(possible_drivers_coordinates))
        coordinates = coordinates_copy
        print('new coord: {}'.format(coordinates))

        # Create riders
        for i in range(drivers_number, number_of_participants[event]):
            print('riders i: {}'.format(i))
            participant_name = "Participant" + str(i)
            # add participant to database
            participantRef = db.collection("users").document(participant_name)
            p_data = {}
            p_data["username"] = participant_name
            p_data["events"] = [eventRef]
            participantRef.set(p_data)


            p_data = {}
            p_data["driver"] = False
            p_data["username"] = participant_name
            participantInDocRef = eventRef.collection("participants").document(participant_name)
            print('driv_num + len: {}'.format(drivers_number + len(riders_coordinates)))
            if i < drivers_number + len(riders_coordinates):
                participant_latitude, participant_longitude = riders_coordinates[i - drivers_number]
                participantMap = {"LatLng": firestore.GeoPoint(participant_latitude, participant_longitude),
                                  "street": riders_addresses[i - drivers_number]}
            else:
                index_random = getRandomInt(0, len(coordinates)-1)
                print('riders random: {}'.format(index_random))
                participant_latitude, participant_longitude = coordinates[index_random]
                riders_coordinates.append(coordinates[index_random])
                del coordinates[index_random]
                participantMap = {"LatLng": firestore.GeoPoint(participant_latitude, participant_longitude),
                                  "street": addresses[index_random]}
                riders_addresses.append(addresses[index_random])
                del addresses[index_random]
            p_data["start"] = participantMap
            # add participant to event
            participantInDocRef.set(p_data)

        print('---------------')
        print('rid add: {}'.format(riders_addresses))
        print('new add: {}'.format(addresses))
        print()
        print('rid coord: {}'.format(riders_coordinates))
        print('new coord: {}'.format(coordinates))
        print()
        print('--------------')

        data["participants"] = number_of_participants[event]
        data["drivers"] = drivers
        eventRef.set(data)


def getRandomInt(minimum, maximum):
    return randint(minimum, max(0, maximum))


if __name__ == '__main__':
    generateEvent()