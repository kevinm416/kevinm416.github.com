import Data.Array.IArray

editDistance :: Eq a => Array Int a -> Array Int a -> Int
editDistance s t = edit ! (x, y)
    where recurrence :: (Int, Int) -> Int
          recurrence (0, j) = j
          recurrence (i, 0) = i
          recurrence (i, j) = minimum [edit ! (i-1, j-1) + penalty,
                                       edit ! (i-1, j) + 1,
                                       edit ! (i, j-1) + 1]
              where penalty = if (s ! i) == (t ! j) then 0 else 1

          (_, x) = bounds s
          (_, y) = bounds t
          size = ((0,0), (x,y))

          edit :: Array (Int, Int) Int
          edit = array size [(coord, recurrence coord) | coord <- range size]

main = do
    let w1 = "Saturday"
        w2 = "Sunday"
        a1 = listArray (1, length w1) w1
        a2 = listArray (1, length w2) w2
    print $ editDistance a1 a2
