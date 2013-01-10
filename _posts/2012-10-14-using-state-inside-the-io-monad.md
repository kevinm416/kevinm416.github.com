---
layout: post
title: Using IO Inside the State Monad
---

# {{ page.title }}

For a new user of Haskell, it can be very difficult to realize that the
solution to interleaving the effects of different monads is solved by
the monad transformer libraries (mtl). This is the example that motivated
me to discover what mtl is.

### What is State?

The `State` monad is a way to perform stateful computations within Haskell,
despite Haskell being a purely functional language. The "state" is the
parameters that influence a function's computation, but are not passed
explicitly to the function. 

For example, the following procedure in C++ takes no arguments. Yet it
returns a different value with each call.
{% highlight java %}
public int stateful() {
	static int x = 0;
	return x++;
}
{% endhighlight %}
The variable `x` is part of the procedure's state. State in procedural 
languages often includes global variables, or even information on the file 
system, since they are easy to access. As a pure functional language, 
Haskell does not allow functions to access arbitrary state. However, it
can be necessary or helpful to keep state.

### State in Haskell

The key is to know ahead of time what state your function needs.
For example, your program might need a `Map String Int` that gets updated
all over the place. I am sure you can see how you could modify each
function to take an extra paramter that contains all the state necessary,
and returns an updated state as well as the usual return value, perhaps 
as a tuple. This is what the `State` monad does, and its entrypoint is the
`runState` function.

{% highlight haskell %}
runState :: State s r -> s -> (r, s)
{% endhighlight %}

We supply a stateful computation and an initial state to `runState`,
and we get the return value and the state at the end of the computation.
Let's look at an example where the state is a map from natural numbers
to their corresponding alphabet character.

{% highlight haskell %}
import Control.Monad.State
import Data.Map as M
import Data.Char
main = print $ runState (updateState [0..25]) M.empty

updateState :: [Int] -> State (M.Map Int Char) String
updateState (x:xs) = do
	previousState <- get
	put $ M.insert x (chr $ 65 + x) previousState
	updateState xs
updateState [] = return "done"
{% endhighlight %}

The functions `get` and `put` are part of the `State` monad. `get` returns
the current state, and `put` updates the current state, as you might
expect by their names. We mentioned above that a stateful computation 
also has a return value. The `return` function sets the return value.
In this example, the return value of this computation is the string 
`"done"`.

The stateful computation here is `updateState [0..25]`, and the initial
state is `M.empty` (the empty map). Notice how `updateState` has type 
`[Int] -> State (M.Map Int Char) ()`, so `updateState [0..25]` has type 
`State (M.Map Int Char) ()`, exactly what `runState` needs. 

### Monads inside Monads

Now, what happens when we want the user to populate the 
contents of the map, and print out its intermediate contents? To print we
need to be in the `IO` monad, but that would require exiting the
current `State` monad, which we can not do because then we would lose the
map we are building up.

The monad transformer libraries provide a solution to this problem. By
using the monad transformer version of `State`, `StateT`, we can make 
calls to the enclosing monad using `lift`. In this case we want the
enclosing monad to be `IO` so that we can print.

{% highlight haskell %}
main = print =<< runStateT (updateState [0..25]) M.empty

getUserChar :: Int -> IO Char
getUserChar x = do
    putStrLn $ "Enter character for " ++ show x ++ ":"
    c <- getLine
    if length c == 1
        then return $ head c
        else getUserChar x

updateState :: [Int] -> StateT (M.Map Int Char) IO String
updateState (x:xs) = do
    previousState <- get
    c <- lift $ getUserChar x
    let updatedState = M.insert x c previousState
    lift $ print $ "current map: " ++ show updatedState
    put updatedState
    updateState xs
updateState [] = return "done"
{% endhighlight %}

The functions`getUserChar` and `print` both operate in the `IO` monad. 
By applying `lift` to these two functions while inside the `StateT` monad 
transformer, we were able to use the capabilities of the outer monad. In
this case that allowed us to perform IO, but in general we can use
different layering of monads. Let's see how we could rewrite `getUserChar`
using the `MaybeT` monad transformer.

{% highlight haskell %}
import Control.Monad.Trans.Maybe
import Data.Maybe

getValidChar :: Int ->  MaybeT IO Char
getValidChar x = do
    lift $ putStrLn $ "Enter character for " ++ show x ++ ":"
    s <- lift getLine
    guard (length s == 1)
    return $ head s

getUserChar :: Int -> IO Char
getUserChar x = do
    c <- runMaybeT $ msum $ repeat $ getValidChar x
    return $ fromJust c
{% endhighlight %}

`getValidChar` operates in the `MaybeT` monad transformer with
`IO` as the enclosing monad. When we need to prompt the user for input
or read a line from the console, we lift that code into `IO` monad. 

