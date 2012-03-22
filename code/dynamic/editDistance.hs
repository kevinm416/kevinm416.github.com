import qualified Data.Map as M
import qualified Data.Array.IArray as A

editDistance :: (Eq a) => A.Array Int a -> A.Array Int a -> Int
editDistance w1 w2 = 
    let (_, l1) = A.bounds w1
        (_, l2) = A.bounds w2
        baseCases = [((i, 0), i) | i <- [0..l1]] ++ 
                    [((0, j), j) | j <- [0..l2]]
        indices = [(i, j) | i <- [1..l1], j <- [1..l2]]
        table = foldl (\m (k, v) -> M.insert k v m) M.empty baseCases
        edit = foldl ins table indices
        in edit M.! (l1, l2)
    where ins m k@(i, j) = M.insert k (recurrence (w1 A.! i) (w2 A.! j) i j m) m

recurrence :: (Eq a) => a -> a -> Int -> Int -> M.Map (Int, Int) Int -> Int
recurrence c1 c2 i j edit = 
    let subProb1 = edit M.! (i, j-1) + 1
        subProb2 = edit M.! (i-1, j) + 1
        subProb3 = edit M.! (i-1, j-1) + if c1 == c2 then 0 else 1 
        in minimum [subProb1, subProb2, subProb3]

main = do
    let w1 = "Saturday"
        w2 = "Sunday"
        a1 = A.listArray (1, length w1) w1
        a2 = A.listArray (1, length w2) w2
    print $ editDistance a1 a2
