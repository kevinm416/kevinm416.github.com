import Data.String.Utils

findPath :: (Num a, Ord a) => [[a]] -> a
findPath (l1 : l2 : ls) = findPath $ (reduceRow l1 l2) : ls
findPath (l : []) = head l

reduceRow :: (Num a, Ord a) => [a] -> [a] -> [a]
reduceRow (x1 : x2 : xs) (z : zs) = (max x1 x2) + z : reduceRow (x2 : xs) zs
reduceRow _ _ = []

main = do
    file <- readFile "path/to/pyramid.txt"
    let tiers = reverse . map (replace " " ",") $ lines file
        nums = map (\x -> read $ "[" ++ x ++ "]") tiers :: [[Integer]]
    print $ findPath nums