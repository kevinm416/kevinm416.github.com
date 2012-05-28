import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.HashCodeBuilder;

class Car {
    private final int column, row, size;
    private final Direction direction;
    private boolean hashed;
    private int hashCode;

    public Car(int row, int column, int size, Direction direction) {
        Validate.notNull(direction);
        this.column = column;
        this.row = row;
        this.size = size;
        this.direction = direction;
    }

    public boolean intersects(Car other) {
        if (other.getColumnBound() < column 
                || other.column > getColumnBound()
                || other.getRowBound() < row 
                || other.row > getRowBound()) {
            return false;
        }
        return true;
    }
    
    public Car move(int amt) {
        int newColumn = column;
        int newRow = row;
        switch (direction) {
            case HORIZONTAL:
                newColumn += amt;
                break;
            case VERTICAL:
                newRow += amt;
                break;
        }
        return new Car(newRow, newColumn, size, direction);
    }
    
    @Override
    public int hashCode() {
        if (!hashed) {
            HashCodeBuilder hashBuilder = new HashCodeBuilder();
            hashCode = hashBuilder.
                       append(direction.hashCode()).
                       append(size).
                       append(column).
                       append(row).
                       toHashCode();
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
        Car other = (Car) obj;
        if (direction != other.direction)
            return false;
        if (size != other.size)
            return false;
        if (column != other.column)
            return false;
        if (row != other.row)
            return false;
        return true;
    }

    public Direction getDirection() {
        return direction;
    }
    
    public int getColumn() {
        return column;
    }
    
    public int getColumnBound() {
        switch (direction) {
        case VERTICAL:
            return column;
        case HORIZONTAL:
            return column + size - 1;
        }
        throw new IllegalStateException(
                "This cannot happen. All enum types were covered.");
    }
    
    public int getRow() {
        return row;
    }
    
    public int getRowBound() {
        switch (direction) {
        case VERTICAL:
            return row + size - 1;
        case HORIZONTAL:
            return row;
        }
        throw new IllegalStateException(
                "This cannot happen. All enum types were covered.");
    }

    @Override
    public String toString() {
        return "Car [column=" + column + ", row=" + row + ", size=" + size
                + ", direction=" + direction + "]";
    }
}