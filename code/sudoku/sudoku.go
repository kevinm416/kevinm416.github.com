package sudoku

import (
    "log"
    "os"
    "fmt"
    "bytes"
    "strings"
)

const (
    logProperties = log.Ldate | log.Ltime | log.Lshortfile
)

var (
    logger = log.New(os.Stdout, "", logProperties)
)

type digitSet uint16
func EmptyDigitSet() digitSet { return digitSet(0) }
func FullDigitSet() digitSet { return digitSet(0x1FF) }
func NewDigitSet(val int) digitSet {
    if val < 0 || val > 9 {
        panic(fmt.Sprintf("Invalid Digit: %9b", val))
    }
    var ret digitSet
    if val > 0 {
        ret = digitSet(0x1 << (uint(val) - 1))
    } else {
        ret = digitSet(0)
    }
    return ret
}
func (this digitSet) Add(other digitSet) digitSet {
    return digitSet(uint16(this) | uint16(other))
}
func (this digitSet) Contains(other digitSet) bool {
    return uint16(this) & uint16(other) == uint16(other)
}
func (this digitSet) Subtract(other digitSet) digitSet {
    return digitSet(uint16(this) & (^uint16(other)))
}
func (this digitSet) Size() int {
    i := 0
    for ; this > 0; i++ {
        this &= this - 1
    }
    return i
}
func (this digitSet) String() string {
    ret := bytes.NewBufferString("")
    for i := 1; i <= 9; i++ {
        if this.Contains(NewDigitSet(i)) {
            fmt.Fprintf(ret, "%v", i)
        } else {
            fmt.Fprintf(ret, " ")
        }
    }
    return ret.String()
}
func (this digitSet) ToDigit() int {
    if s := this.Size(); s > 1 { 
        panic(fmt.Sprintf(
            "Converting a set with size %v to a digit", s))
    }
    i := 0
    if this == 0 {
        return 0
    } 
    for ; this > 0; i++ {
        if this & 0x1 != 0 {
            break
        }
        this = this >> 1
    }
    return i + 1
}
func (this digitSet) Iterator() chan digitSet {
    c := make(chan digitSet)
    go this.iterHelper(c)
    return c
}
func (this digitSet) iterHelper(c chan digitSet) {
    for i := 1; i <= 9; i++ {
        ds := NewDigitSet(i)
        if this.Contains(ds) {
            c <- ds
        }
    }
    close(c)
}

type puzzleState struct {
    assigned      [9][9]bool
    possibilities [9][9]digitSet
}
func NewPuzzleState(vals string) *puzzleState {
    vals = strings.Replace(vals, "\n", "", -1)
    vals = strings.Replace(vals, " ", "", -1)
    if len(vals) != 81 {
        panic(fmt.Sprintf(
            "Invalid puzzle. len: %v, %v", len(vals), vals))
    }

    assigned := [9][9]bool{}
    possibilities := [9][9]digitSet{}
    for i := 0; i < 9; i++ {
        for j := 0; j < 9; j++ {
            possibilities[i][j] = FullDigitSet()
        }
    }

    ps := &puzzleState{
        assigned: assigned,
        possibilities: possibilities,
        }
    
    for i := 0; i < 81; i++ {
        c := int(vals[i])
        switch {
        case c == '.' || c == '0':
            continue
        case ('1' <= c) && (c <= '9'):
            ps.Assign(NewDigitSet(c - '0'), i/9, i%9)
        default:
            panic(fmt.Sprintf("Invalid puzzle at %v: %v", i, vals))
        }
    }
    return ps
}
func (this *puzzleState) Clone() *puzzleState {
    return &puzzleState{
        assigned: this.assigned,
        possibilities: this.possibilities,
        }
}
func (this *puzzleState) Assign(val digitSet, i, j int) {
    if (val.Size() != 1) {
        panic(fmt.Sprintf(
            "assigning with val.Size(): %v, val: %v", val.Size(), val))
    }
    if this.assigned[i][j] && this.possibilities[i][j] != val {
        panic(fmt.Sprintf(
            "Already assigned. assigning i: %v, j: %v, %v, was %v", 
            i, j, this.possibilities[i][j]))
    }
    if !this.possibilities[i][j].Contains(val) {
        panic(fmt.Sprintf(
            "Inconsistent. assigning i: %v, j: %v, %v, was %v",
            i, j, val, this.possibilities[i][j]))
    }
    this.assigned[i][j] = true
    for k := 0; k < 9; k++ {
        this.possibilities[i][k] = this.possibilities[i][k].Subtract(val)
        this.possibilities[k][j] = this.possibilities[k][j].Subtract(val)
        a, b := (i/3)*3 + k/3, (j/3)*3 + k%3
        this.possibilities[a][b] = this.possibilities[a][b].Subtract(val)
    }
    this.possibilities[i][j] = val

    // Check for positions where there is only 1 possible value
    for k := 0; k < 9; k++ {
        this.checkAssignment(i, k)
        this.checkAssignment(k, j)
        a, b := (i/3)*3 + k/3, (j/3)*3 + k%3
        this.checkAssignment(a, b)
    }
}
func (this *puzzleState) checkAssignment(i, j int) {
    if !this.assigned[i][j] &&
            this.possibilities[i][j].Size() == 1 {
        this.Assign(this.possibilities[i][j], i, j)
    }
}
func (this *puzzleState) Solution() bool {
    for i := 0; i < 9; i++ {
        for j := 0; j < 9; j++ {
            if !this.assigned[i][j] {
                return false
            }
        }
    }
    return true
}
func (this *puzzleState) String() string {
    ret := bytes.NewBufferString("")
    for i := 0; i < 9; i++ {
        for j := 0; j < 9; j++ {
            a := ""
            if this.assigned[i][j] { a = "a" } else { a = " " }
            fmt.Fprintf(ret, "%s.%v ", a, this.possibilities[i][j])
        }
        fmt.Fprintf(ret, "\n")
    }
    return ret.String()
}
func (this *puzzleState) Equals(other *puzzleState) bool {
    for i := 0; i < 9; i++ {
        for j := 0; j < 9; j++ {
            if this.possibilities[i][j] != other.possibilities[i][j] ||
                    this.assigned[i][j] != other.assigned[i][j] {
                return false        
            }
        }
    }
    return true
}

func Solve(puzzle *puzzleState) (slnState *puzzleState, ok bool) {
    defer func() {
        if r := recover(); r != nil {
            logger.Println("error: %v", r)
            slnState, ok = puzzle, false
        }
    }()
    slnState, ok = search(puzzle)
    return
}
func search(puzzle *puzzleState) (*puzzleState, bool) {
    if puzzle.Solution() {
        return puzzle, true
    }
    vals, i, j := minimumRemainingValues(puzzle)
    for val := range vals.Iterator() {
        newState := puzzle.Clone()
        newState.Assign(val, i, j)

        slnState, ok := search(newState)
        if ok {
            return slnState, true
        }
    }
    return nil, false
}
func minimumRemainingValues(puzzle *puzzleState) (digitSet, int, int) {
    var a, b int
    minSize := 10
    for i := 0; i < 9; i++ {
        for j := 0; j < 9; j++ {
            if !puzzle.assigned[i][j] {
                s := puzzle.possibilities[i][j].Size()
                if s < minSize {
                    a, b = i, j
                    minSize = s
                }
            }
        }
    }
    return puzzle.possibilities[a][b], a, b
}