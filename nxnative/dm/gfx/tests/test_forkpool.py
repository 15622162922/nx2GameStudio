"""The pool without semaphores (dm/gfx/tools/forkpool.py): the work runs in forked children, the results come back in
the items' order, and the children get what the pool's initializer gives them."""
import importlib.util
import os
import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('forkpool', REPO / 'dm/gfx/tools/forkpool.py')
forkpool = importlib.util.module_from_spec(spec); spec.loader.exec_module(forkpool)


class ForkMap(unittest.TestCase):

    def test_order_kept(self):
        # the results come back in the items' order, whatever the chunks did
        got = forkpool.fork_map(lambda x: x * 2, list(range(50)), jobs=8)
        self.assertEqual(got, [x * 2 for x in range(50)])

    def test_initializer_runs_in_the_child(self):
        # the children inherit what the pool's initializer sets up (the shader split's globals)
        state = {}
        def init():
            state['set'] = True
            forkpool_state['v'] = 7
        forkpool_state = {}
        got = forkpool.fork_map(lambda x: forkpool_state.get('v'), list(range(10)), jobs=4, init=init)
        self.assertEqual(got, [7] * 10)   # the parent's own state is untouched
        self.assertNotIn('v', state)

    def test_fewer_jobs_than_items(self):
        self.assertEqual(forkpool.fork_map(lambda x: x + 1, [1, 2, 3], jobs=1), [2, 3, 4])

    def test_empty(self):
        self.assertEqual(forkpool.fork_map(lambda x: x, [], jobs=4), [])

    def test_a_child_that_dies_is_reported(self):
        def boom(x):
            if x == 3: os._exit(1)
            return x
        with self.assertRaises(RuntimeError):
            forkpool.fork_map(boom, list(range(8)), jobs=4)


if __name__ == '__main__':
    unittest.main()
