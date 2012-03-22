import Data.List (inits)
import Data.List.Extras.Argmax (argmax)

incSubSeq :: (Num a, Ord a) => [a] -> a
incSubSeq [] = error "Empty List"
incSubSeq lst = last $ foldl l [1] intermediates
    where intermediates = drop 2 (inits lst)
          l q a = q ++ [nextQ a q]

nextQ :: (Num a, Ord a) => [a] -> [a] -> a
nextQ a q = 1 + (snd $ argmax snd $ filtered)
    where aMax = last a
          filtered = (aMax, 0) : (filter (\x -> fst x < aMax) $ zip (init a) q)

main = do
    print $ incSubSeq [1, 2, 3, 0, 1, 2, 0, 1, 2, 5, 4, 6]