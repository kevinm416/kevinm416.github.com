---
layout: post
title: Solving Sudoku With Go
---

# {{ page.title }}

26 Dec 2011

A game of Sudoku requires that we satisfy the constraints that every row, column and 3x3 section contain the digits 1-9. We can approach finding the solution to an instance of a Sudoku puzzle as a search problem. For the uninterrupted code, see [sudoku-golang](http://www.github.com/kevinm416/sudoku-golang).

### Overall Strategy

We will maintain a set of possible digits for each location on the board that has not been assigned a digit. Whenever the set of possible digits for a position has size 1, we know that the position needs to be assigned the value remaining in the set of possible digits.  

### Sets of Digits

We need to maintain a set of possible digits for every position on the Sudoku board. Knowing the range of digit possibilities is limited to 1-9 can help us design a set with very fast operations. Let's make `digitSet` a 9 bit long binary string, where a 1 in the n<sup>th</sup> position means that n is included in the set.

{% highlight text %}
{}              = 000000000
{6}             = 000100000
{2, 7, 8}       = 011000010
{1, 3, 4, 5, 9} = 100011101
{% endhighlight %}

{% highlight go %}
type digitSet uint16
func EmptyDigitSet() digitSet { return digitSet(0) }
func FullDigitSet() digitSet { return digitSet(0x1FF) }
func NewDigitSet(val int) digitSet {
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
{% endhighlight %}

The only operation that does not take constant time is counting the elements in the set. We need to count the numbe of 1's in the binary representation of the set, so finding the size of the set takes time proportional to the size of the set. However, even this cost could be reduced by making digitSet a struct, and adding a `uint8` to track the set's size.

{% highlight go %}
func (this digitSet) Size() int {
    i := 0
    for ; this > 0; i++ {
        this &= this - 1
    }
    return i
}
{% endhighlight %}

The iterator for `digitSet` is interesting because the standard way to create an iterator in Go involves goroutines. The range keyword in Go can only be used on arrays, pointers to arrays, slices, strings, maps, and channels. To create an iterator in Go, we generally create a channel, start another goroutine to send values over that channel, and then return the channel.

{% highlight go %}
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
{% endhighlight %}

### Puzzle State

The state of the puzzle depends on what values are still possible at each of the positions on the Sudoku board. So each board position needs a `digitSet`.

{% highlight go %}
type puzzleState struct {
    assigned      [9][9]bool
    possibilities [9][9]digitSet
}
{% endhighlight %} 

It was not absolutely necessary to track which positions have been assigned, since whenever a `digitSet` in `possibilities` has size 1, it is assigned. However, it makes some of the code nicer.

Next we need to parse strings to construct the initial puzzle state. The string representation we will use is an 81 character string, where each 9 character segment is one row of the Sudoku board. We will allow '0' or '.' be used to represent empty positions. 

{% highlight go %}
func NewPuzzleState(vals string) *puzzleState {
    vals = strings.Replace(vals, "\n", "", -1)
    vals = strings.Replace(vals, " ", "", -1)
    if len(vals) != 81 {
        panic(fmt.Sprintf("Invalid puzzle. len: %v, %v", len(vals), vals))
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
{% endhighlight %}

Every time we make an assignment, we need to eliminate the assigned value from all `digitSet`s in the row, column, and section in which the assignment was made. After eliminating those values, we may have reduced the possible values at another position to a single element. If this is the case, we can mark that position as assigned, and repeat the process.

{% highlight go %}
func (this *puzzleState) Assign(val digitSet, i, j int) {
    if !this.possibilities[i][j].Contains(val) {
        panic(fmt.Sprintf("Inconsistent. assigning i: %v, j: %v, %v, was %v",
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
{% endhighlight %}

### Search

This process is actually good enough to solve most easy to medium difficulty Sudoku puzzles. To solve all Sudoku puzzles, we need to implement the search algorithm. We will use recursive depth first search with the minimum remaining values heuristic. 

To keep assignments from affecting puzzle states at higher levels of the recursive search, we need a `Clone` method for `puzzleState`. Array assignment in Go copies the array, so the original `puzzleState` will not be affected by any changes made to its clone. 

{% highlight go %}
func (this *puzzleState) Clone() *puzzleState {
    return &puzzleState{
        assigned: this.assigned,
        possibilities: this.possibilities,
        }
}
{% endhighlight %}

Now we are ready to implement `search`. The `search` function assigns an unassigned position one of its possible values, then recurses. If we are able to assign all positions a value, then we have a solution, since we are only assigning valid values. If all possible assignments for one variable do not lead to a solution, then we return false as the second argument so signify that the current path is a dead end.

{% highlight go %}
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
{% endhighlight %}

We want to wrap `search` so that we can catch any panics. The panics result from incorrectly formed puzzles, so it is safe to return that no solution exists to such a puzzle.

{% highlight go %}
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
{% endhighlight %}
