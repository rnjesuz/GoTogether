import random
import itertools
from ortools.linear_solver import pywraplp

def main():
    solver = pywraplp.Solver('SolveIntegerProblem',
                             pywraplp.Solver.CBC_MIXED_INTEGER_PROGRAMMING)

    #drivers = ["Andy", "Buzz", "Scar", "Alladin", "Jasmin", "Ariel", "Sora", "Kairi"]
    drivers = ["Andy", "Buzz"]
    drivers_seats = [3, 2, 5, 4, 3, 0, 1, 4]
    #riders = ["Woody", "Olaf", "Jack", "Flint", "Linguini", "Remy", "Simba", "Sebastian"]
    riders = ["Woody"]
    participants = drivers + riders

    # all possible combinations S of 1 driver and up to x riders, where x = empty seats.
    combinations = []
    for index in range(len(drivers)):
        # for each driver get every possible combination of passengers (which includes riders and drivers)
        if len([x for i,x in enumerate(participants) if i != index]) < drivers_seats[index]:
            combinations.append(list(itertools.combinations([x for i,x in enumerate(participants) if i != index], len([x for i,x in enumerate(participants) if i != index]))))
        else:
            combinations.append(list(itertools.combinations([x for i,x in enumerate(participants) if i != index], drivers_seats[index])))

    print(combinations)

    # the total distance of the shortest route with the driver picking up all riders and taking them to the destination
    distances = []
    for i in range(len(drivers)):
        distances.append([])
        for j in range(len(combinations[i])):
            # TODO remove random
            distances[i].append(random.randint(0, 200000))

    # binary integer variables for the problem
    x = {}
    for i in range(len(drivers)):
        for j in range(len(combinations[i])):
            x[i, j] = solver.BoolVar('x[%i, %i]' % (i, j))

    # Objective
    # Minimize the total distance covered
    solver.Minimize(solver.Sum([distances[i][j] * x[i, j] for i in range(len(drivers))
                                for j in range(len(combinations[i]))]))

    # Constraints
    # Each participant is selected exactly once
    for i in range(len(drivers)):
        solver.Add(solver.Sum([x[i, j] for j in range(len(combinations[i]))]) == 1)

    # Solve
    sol = solver.Solve()

    print('Total cost = ', solver.Objective().Value())
    print()
    for i in range(len(drivers)):
        for j in range(len(combinations[i])):
            if x[i, j].solution_value() > 0:
                print('Worker %d assigned to task %d.  Cost = %d' % (
                    i,
                    j,
                    distances[i][j]))

    print()
    print("Time = ", solver.WallTime(), " milliseconds")


if __name__ == '__main__':
    main()
