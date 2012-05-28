import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.lang3.Validate;

import com.google.common.collect.*;

public class Puzzle {
    
    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Usage: [/path/to/puzzle]");
            System.exit(1);
        }
        Puzzle p = Puzzle.ParseFile(args[0]);
        List<PuzzleState> res = p.aStar();
        System.out.println(p.prettyPrintSolution(res));
    }
    
    public static final String SPECIAL_CAR_NAME = "S";
    private static final Pattern boardDimensionsRE = Pattern.compile("\\s*(\\d*)\\s*(\\d*)\\s*");
    private static final Pattern carPositionRE = Pattern.compile("\\s*(\\w*)\\s*(\\d*)\\s*(\\d*)\\s*(\\d*)\\s*(\\w*)\\s*");
    
    private final PuzzleState startState;
    private final List<String> carNames;

    private Puzzle(Builder b) {
        this.startState = new PuzzleState(null, 0, b.rows, b.columns, b.cars);
        this.carNames = b.carNames;
    }
    
    private List<PuzzleState> getPath(PuzzleState currentState) {
        List<PuzzleState> path = Lists.newArrayList();
        while (currentState != null) {
            path.add(currentState);
            currentState = currentState.getParent();
        }
        Collections.reverse(path);
        return path;
    }
    
    public List<PuzzleState> aStar() {
        Queue<PuzzleState> openSet = new PriorityQueue<PuzzleState>();
        Set<PuzzleState> closedSet = Sets.newHashSet();
        
        openSet.add(startState);
        PuzzleState currentState;
        while (!openSet.isEmpty()) {
            currentState = openSet.poll();
            if (currentState.isSolution()) {
                return getPath(currentState);
            }
            if (!closedSet.contains(currentState)) {
                closedSet.add(currentState);
                List<PuzzleState> children = currentState.getChildren();
                for (PuzzleState child : children) {
                    if (!closedSet.contains(child)) {
                        openSet.add(child);
                    }
                }
            }
        }
        throw new IllegalArgumentException("No solutions");
    }

    private static void parseDimensions(Builder puzzleBuilder,
            BufferedReader input) throws IOException {
        String firstLine = input.readLine();
        Matcher dimensionMatcher = boardDimensionsRE.matcher(firstLine);
        dimensionMatcher.matches();
        int rows = Integer.parseInt(dimensionMatcher.group(1));
        int columns = Integer.parseInt(dimensionMatcher.group(2));
        puzzleBuilder.setRows(rows).setColumns(columns);
    }

    private static void parseCars(Builder puzzleBuilder, BufferedReader input)
            throws IOException {
        int specialCarCount = 0;
        List<Car> cars = Lists.newArrayList();
        List<String> carNames = Lists.newArrayList();
        String line;
        while ((line = input.readLine()) != null) {
            Matcher carMatcher = carPositionRE.matcher(line);
            carMatcher.matches();
            String carName = carMatcher.group(1);
            int carLength = Integer.valueOf(carMatcher.group(2));
            int startRow = Integer.valueOf(carMatcher.group(3));
            int startCol = Integer.valueOf(carMatcher.group(4));
            Direction startDir = Direction.valueOf(carMatcher.group(5).toUpperCase());

            Car newCar = new Car(startRow, startCol, carLength, startDir);
            if (carName.equals(SPECIAL_CAR_NAME)) {
                specialCarCount++;
                Validate.isTrue(startDir == Direction.HORIZONTAL,
                        "Special car must be oriented horizontally");
                cars.add(0, newCar);
                carNames.add(0, carName);
            } else {
                cars.add(newCar);
                carNames.add(carName);
            }
        }
        Validate.isTrue(specialCarCount == 1, 
                "There are: " + specialCarCount + " special cars. There can only be 1.");
        puzzleBuilder.setCars(cars).setCarNames(carNames);
    }

    public static Puzzle ParseFile(String fileName) {
        Builder puzzleBuilder = new Builder();
        try {
            BufferedReader input = new BufferedReader(new FileReader(new File(fileName)));
            parseDimensions(puzzleBuilder, input);
            parseCars(puzzleBuilder, input);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("File: " + fileName
                    + " does not exist", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error parsing file: "
                    + fileName, e);
        }
        return puzzleBuilder.build();
    }

    private static class Builder {
        Integer rows, columns;
        ArrayList<Car> cars;
        ArrayList<String> carNames;
        
        public Puzzle build() {
            validate();
            return new Puzzle(this);
        }
        
        private void validate() {
            Validate.notNull(rows);
            Validate.notNull(columns);
            Validate.notNull(cars);
            Validate.notNull(carNames);
            for (int i = 0; i < cars.size(); i++) {
                Car car = cars.get(i);
                Validate.isTrue(car.getColumn() >= 0, car.toString());
                Validate.isTrue(car.getColumnBound() < columns, car.toString());
                Validate.isTrue(car.getRow() >= 0, car.toString());
                Validate.isTrue(car.getRowBound() < rows, car.toString());
                for (int j = 0; j < cars.size(); j++) {
                    Validate.isTrue(i == j || !car.intersects(cars.get(j)), 
                            "Car: " + car + " intersects with: " + cars.get(j));
                }
            }
        }
        
        public Builder setRows(int rows) {
            this.rows = rows; return this;
        }
        public Builder setColumns(int columns) {
            this.columns = columns; return this;
        }
        public Builder setCars(List<Car> cars) {
            this.cars = Lists.newArrayList(cars); return this;
        }
        public Builder setCarNames(List<String> carNames) {
            this.carNames = Lists.newArrayList(carNames); return this;
        }
    }
    
    public String prettyPrintSolution(List<PuzzleState> solution) {
        StringBuilder ret = new StringBuilder();
        for (PuzzleState puzzleState : solution) {
            ret.append(puzzleState.prettyPrint(carNames));
            ret.append("===========\n");
        }
        return ret.toString();
    }
}
