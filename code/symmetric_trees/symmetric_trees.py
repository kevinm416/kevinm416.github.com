
class Node:
    def __init__(self, label=None, left=None, right=None):
        self.left = left
        self.right = right
        self.label = label

    def __str__(self):
        return "Node %s." % str(self.label)

def label_binary_tree(root):
    (left, right, label) = (None, None, 0)
    if root.left:
        left = label_binary_tree(root.left)
        label += left.label
    if root.right:
        right = label_binary_tree(root.right)
        label += right.label
    return Node(label + 1, left, right)

def in_order_list(root):
    def in_order_helper(root, lst):
        if not root: return
        in_order_helper(root.left, lst);
        lst.append(root.label)
        in_order_helper(root.right, lst);
        return lst
    return in_order_helper(root, [])

def is_palindrome(seq):
    for i in xrange(len(seq)/2):
        if not seq[i] == seq[-(i+1)]:
            return False
    return True

def is_symmetric_binary_tree(x): 
    return is_palindrome(in_order_list(label_binary_tree(x)))

def reconstruct_binary_tree(lst):
    if lst == []: return None
    max_val, max_idx = None, None
    for (idx, val) in enumerate(lst):
        if val > max_val:
            max_idx, max_val = idx, val
    return Node(val, 
        reconstruct_binary_tree(lst[:idx]),
        reconstruct_binary_tree(lst[idx+1:]))


if __name__ == '__main__':
    
    a = Node('a', 
            Node('b',
                Node('c'),
                Node('d')),
            Node('e',
                Node('f'),
                Node('g')))
    
    print is_symmetric_binary_tree(a)

    print in_order_list(label_binary_tree(a))
    print in_order_list(reconstruct_binary_tree(in_order_list(label_binary_tree(a))))
