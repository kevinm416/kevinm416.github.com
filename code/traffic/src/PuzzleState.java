import java.util.*;

import org.apache.commons.lang3.Validate;

import com.google.common.collect.Lists;

class PuzzleState implements Comparable<PuzzleState> {
    private final PuzzleState parent;
    private final int pathLength;
    private final List<Car> cars;
    private final int heuristic;
    private final int rows, columns;
    private boolean hashed = false;
    private int hashCode;

    public PuzzleState(PuzzleState parent, int pathLength, int rows, int columns, List<Car> cars) {
        Validate.notNull(cars);
        Validate.isTrue(cars.size() > 0);
        this.parent = parent;
        this.pathLength = pathLength;
        this.cars = cars;
        this.rows = rows;
        this.columns = columns;
        heuristic = calculateHeuristic();
    }
    
    private boolean isBlockingExit(Car specialCar, Car otherCar) {
        switch(otherCar.getDirection()) {
            case VERTICAL:
                if (otherCar.getRow() >= specialCar.getRow()
                        && otherCar.getRowBound() <= specialCar.getRow()) {
                    return true;
                }
            default:
                return false;
        }
    }
    
    private int calculateHeuristic() {
        int blockingCount = 0;
        Car specialCar = cars.get(0);
        for (int i = 1; i < cars.size(); i++) {
            Car current = cars.get(i);
            if (isBlockingExit(specialCar, current)) {
                blockingCount++;
            }
        }
        if (!isSolution()) {
            blockingCount += 1; 
        }
        return blockingCount;
    }
    
    public boolean isSolution() {
        return cars.get(0).getColumnBound() == columns - 1;
    }

    private PuzzleBitmap fillBitmap() {
        PuzzleBitmap bitmap = new PuzzleBitmap(rows, columns);
        for (Car car : cars) {
            bitmap.addCar(car);
        }
        return bitmap;
    }
    
    private List<PuzzleState> horizontalMoves(PuzzleBitmap bm, Car movingCar, int movingCarIdx) {
        List<PuzzleState> horizontalMoves = Lists.newArrayList();
        // move left
        for (int i = 1; i <= movingCar.getColumn(); i++) {
            if (!bm.get(movingCar.getColumn() - i, movingCar.getRow())) {
                ArrayList<Car> newCars = Lists.newArrayList(cars); 
                newCars.set(movingCarIdx, movingCar.move(-i));
                horizontalMoves.add(new PuzzleState(this, pathLength + 1, rows, columns, newCars));
            } else {
                break; // blocked by another car
            }
        }
        // move right
        for (int i = 1; i < columns - movingCar.getColumnBound(); i++) {
            if (!bm.get(movingCar.getColumnBound() + i, movingCar.getRow())) {
                ArrayList<Car> newCars = Lists.newArrayList(cars);
                newCars.set(movingCarIdx, movingCar.move(i));
                horizontalMoves.add(new PuzzleState(this, pathLength + 1, rows, columns, newCars));
            } else {
                break; // blocked by another car
            }
        }
        return horizontalMoves;
    }

    private List<PuzzleState> verticalMoves(PuzzleBitmap bm, Car movingCar, int movingCarIdx) {
        List<PuzzleState> verticalMoves = Lists.newArrayList();
        // move up
        for (int i = 1; i <= movingCar.getRow(); i++) {
            if (!bm.get(movingCar.getColumn(), movingCar.getRow() - i)) {
                ArrayList<Car> newCars = Lists.newArrayList(cars);
                newCars.set(movingCarIdx, movingCar.move(-i));
                verticalMoves.add(new PuzzleState(this, pathLength + 1, rows, columns, newCars));
            } else {
                break; // blocked by another car
            }
        }
        // move down
        for (int i = 1; i < rows - movingCar.getRowBound(); i++) {
            if (!bm.get(movingCar.getColumn(), movingCar.getRowBound() + i)) {
                ArrayList<Car> newCars = Lists.newArrayList(cars);
                newCars.set(movingCarIdx, movingCar.move(i));
                verticalMoves.add(new PuzzleState(this, pathLength + 1, rows, columns, newCars));
            } else {
                break; // blocked by another car
            }
        }
        return verticalMoves;
    }

    public List<PuzzleState> getChildren() {
        List<PuzzleState> children = Lists.newArrayList();
        PuzzleBitmap board = fillBitmap();
        for (int i = 0; i < cars.size(); i++) {
            Car movingCar = cars.get(i);
            switch (movingCar.getDirection()) {
                case HORIZONTAL:
                    children.addAll(horizontalMoves(board, movingCar, i));
                    break;
                case VERTICAL:
                    children.addAll(verticalMoves(board, movingCar, i));
                    break;
            }
        }
        return children;
    }
    
    @Override
    public int compareTo(PuzzleState o) {
        int thisCost = pathLength + heuristic;
        int otherCost = o.pathLength + o.heuristic;
        return thisCost - otherCost;
    }
    
    @Override
    public int hashCode() {
        if (!hashed) {
            hashCode = cars.hashCode();
            hashed = true;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PuzzleState other = (PuzzleState) obj;
        if (!cars.equals(other.cars))
            return false;
        return true;
    }
    
    public int getHeuristic() {
        return heuristic;
    }
    
    public PuzzleState getParent() {
        return parent;
    }
    
    public int getPathLength() {
        return pathLength;
    }
    
    private static class PuzzleBitmap {
        private boolean[][] carPositions;

        private PuzzleBitmap(int rows, int columns) {
            carPositions = new boolean[rows][columns];
        }

        public void addCar(Car car) {
            switch (car.getDirection()) {
                case HORIZONTAL:
                    for (int i = car.getColumn(); i <= car.getColumnBound(); i++) {
                        carPositions[car.getRow()][i] = true;
                    }
                    break;
                case VERTICAL:
                    for (int i = car.getRow(); i <= car.getRowBound(); i++) {
                        carPositions[i][car.getColumn()] = true;
                    }
                    break;
            }
        }

        public boolean get(int x, int y) {
            return carPositions[y][x];
        }
    }
    
    public String prettyPrint(List<String> carNames) {
        String[][] board = new String[rows][columns];
        for (int k = 0; k < cars.size(); k++) {
            Car car = cars.get(k);
            switch (car.getDirection()) {
                case HORIZONTAL:
                    for (int i = car.getColumn(); i <= car.getColumnBound(); i++) {
                        board[car.getRow()][i] = carNames.get(k);
                    }
                    break;
                case VERTICAL:
                    for (int i = car.getRow(); i <= car.getRowBound(); i++) {
                        board[i][car.getColumn()] = carNames.get(k);
                    }
                    break;
            }
        }
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                if (board[i][j] != null) {
                    ret.append(board[i][j]);
                } else {
                    ret.append("_");
                }
                ret.append(" ");
            }
            ret.append("\n");
        }
        return ret.toString();
    }
    
    @Override
    public String toString() {
        int[][] board = new int[rows][columns];
        for (int k = 0; k < cars.size(); k++) {
            Car car = cars.get(k);
            switch (car.getDirection()) {
                case HORIZONTAL:
                    for (int i = car.getColumn(); i <= car.getColumnBound(); i++) {
                        board[car.getRow()][i] = k + 1;
                    }
                    break;
                case VERTICAL:
                    for (int i = car.getRow(); i <= car.getRowBound(); i++) {
                        board[i][car.getColumn()] = k + 1;
                    }
                    break;
            }
        }
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < board.length; i++) {
            ret.append(Arrays.toString(board[i]));
            ret.append("\n");
        }
        return ret.toString();
    }
}
