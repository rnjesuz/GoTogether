import random
import itertools
from ortools.linear_solver import pywraplp

def main():
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

    drivers = ["Andy", "Buzz", "Scar", "Alladin", "Jasmin", "Ariel", "Sora", "Kairi"]
    # drivers = ["Andy", "Buzz", "Scar"]
    drivers_seats = [3, 2, 5, 4, 3, 0, 1, 4]
    riders = ["Woody", "Olaf", "Jack", "Flint", "Linguini", "Remy", "Simba", "Sebastian"]
    # riders = ["Woody"]
    participants = drivers + riders
    print("Participants: {}".format(participants))

    # all possible combinations S of 1 driver and up to x riders, where x = empty seats.
    possible_combinations = []
    distances = []
    x = {}
    for index in range(len(drivers)):
        # for each driver get every possible combination of passengers (which includes riders and drivers)
        remaining_participants = [x for i, x in enumerate(participants) if i != index]
        possible_combinations.append([])
        distances.append([])
        for seats in range(0, drivers_seats[index]+1):
            permutation = [tuple([drivers[index]]+list(tup)) for tup in combinations(remaining_participants, seats)]
            possible_combinations[index] += permutation
        for j in range(0, len(possible_combinations[index])):
            distances[index].append(random.randint(0, 200000))
            x[index, j] = solver.BoolVar('x[%i,%i]' % (index, j))

    print("Possible combinations: {}".format(possible_combinations))

    '''# the total distance of the shortest route with the driver picking up all riders and taking them to the destination
    distances = []
    for i in range(len(drivers)):
        distances.append([])
        for j in range(len(possible_combinations[i])):
            # TODO remove random
            distances[i].append(random.randint(0, 200000))

    # Binary integer variables for the problem (0 or 1)
    # 1 means the combination was chose
    x = {}
    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
            x[i, j] = solver.BoolVar('x[%i,%i]' % (i, j))
    print("x: {}".format(x))'''

    # Objective
    # Minimize the total distance covered
    solver.Minimize(solver.Sum([distances[i][j] * x[i, j] for i in range(len(drivers))
                                for j in range(len(possible_combinations[i]))]))

    # Constraints
    # TODO all participants are present in the chosen combinations

    # Each participant is selected exactly once
    # TODO isto não está correcto
    for participant in participants:
        solver.Add(solver.Sum([x[i, j] for i in range(len(drivers)) for j in range(len(possible_combinations[i])) if participant in possible_combinations[i][j]]) == 1)
    #for i in range(len(drivers)):
    #    solver.Add(solver.Sum([x[i, j] for j in range(len(possible_combinations[i]))]) == 1)

    '''for participant in participants:
        participant_x = {}
        for item in x.items():
            if participant in possible_combinations[item[0][0]][item[0][1]]:
                participant_x[item[0]] = solver.BoolVar('x[%i,%i]' % (item[0][0], item[0][1]))
        solver.Add(solver.Sum(participant_x) == 1)'''

    #solver.Add(solver.Sum(x[i, j]) == 1)

    # Solve
    sol = solver.Solve()

    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
            if x[i, j].solution_value() > 0:
                print("x[{},{}]: {}".format(i,j,x[i, j].solution_value()))

    print('Total cost = ', solver.Objective().Value())
    print()
    for i in range(len(drivers)):
        for j in range(len(possible_combinations[i])):
            if x[i, j].solution_value() > 0:
                print('Worker %d assigned to task %d.  Cost = %d' % (
                    i,
                    j,
                    distances[i][j]))

    print()
    print("Time = ", solver.WallTime(), " milliseconds")


def combinations(items, howmany):
    return list(itertools.combinations(items, min(howmany, len(items))))


if __name__ == '__main__':
    main()
