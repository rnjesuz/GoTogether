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
    event_name = ["DFar PFar with 5", "DFar PFar with 7", "DFar PFar with 10", "DFar PFar with 12", "DFar PFar with 15"]
    event_destination = "Largo Sé Velha 33, 3000-383 Coimbra"
    destination_latitude, destination_longitude = (40.208834, -8.427373)
    coordinates = [(38.722335, -9.139773),  # Lisboa
                   (38.738028, -9.155097),
                   (38.767151, -9.099683),

                   (38.798900, -9.387650),  # Sintra
                   (38.802866, -9.381769),
                   (38.825655, -9.469261),

                   (38.691738, -9.311921),  # Oeiras
                   (38.692823, -9.314232),
                   (38.693980, -9.292844),

                   (38.696888, -9.420353),  # Cascais
                   (38.728800, -9.474545),
                   (38.707996, -9.397243),

                   (38.752400, -9.233935),  # Amadora
                   (38.760034, -9.235858),
                   (38.739647, -9.215205)
                   ]

    addresses = ["Campo Mártires da Pátria, Lisboa",
                 "Av. de Berna, 1050-043 Lisboa",
                 "Av. Berlim, Lisboa",

                 "R. Dr. Alfredo da Costa 5, 2710-631 Sintra",
                 "N249 67-49, 2710-589 Sintra",
                 "R. Pedro Álvares Cabral, Colares",

                 "Largo 5 de Outubro 3, 2780-293 Oeiras",
                 "Largo Marquês Pombal, Oeiras",
                 "Av. Marquês Pombal, 2770-066 Paço de Arcos",

                 "Praça 5 de Outubro, 2750-642 Cascais",
                 "N247 13, 2750-642 Cascais",
                 "Praça Almeida Garrett, Estoril",

                 "Av. Conde Castro Guimarães 6, 2700-260 Amadora",
                 "Av. Cardoso Lopes 12-18, 2700-159 Amadora",
                 "Av. da Força Aérea Portuguesa, Amadora"
                 ]

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