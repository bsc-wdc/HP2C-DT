import unittest
import os

# Define the path to the tests directory
test_dir = os.path.join(os.path.dirname(__file__), 'tests')


# Discover and run all tests in the /tests/ directory
def run_tests():
    # Discover all tests in the 'tests' folder
    loader = unittest.defaultTestLoader
    suite = loader.discover(test_dir, pattern="*.py")

    # Create a test runner and run the discovered tests
    runner = unittest.TextTestRunner()
    runner.run(suite)


if __name__ == "__main__":
    run_tests()
