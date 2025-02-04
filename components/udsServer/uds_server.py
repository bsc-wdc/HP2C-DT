"""
Deploy a Unix socket server that waits indefinitely for petitions of Edge nodes
to execute external functions in the `funcs` directory.
"""
import sys
import socket
import json
import importlib.util
import os
import traceback


DEFAULT_METHOD_NAME = "main"

# Load funcs directory into PATH
curr_module_path = os.path.abspath(__file__)
curr_dir_path = os.sep.join(curr_module_path.split(os.sep)[:-1])
sys.path.append(os.path.join(curr_dir_path, "funcs"))


def import_function(module_name, function_name):
    """ Return handler of a function in external module by name. """
    module = importlib.import_module(module_name)
    return getattr(module, function_name)


def main(socket_path, func_module, json_params, buffer_size=1024):
    print("Instantiating socket", socket_path)
    print("func_module:", func_module)
    print("json_params:", json_params)
    print("buffer_size:", buffer_size)

    # Remove socket if it exists and bind
    if os.path.exists(socket_path):
        os.remove(socket_path)
    server_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server_socket.bind(socket_path)
    server_socket.listen(1)

    print(f"Server listening on {socket_path}")

    # Start loop to accept requests
    client_socket, _ = server_socket.accept()
    try:
        with client_socket:
            while True:
                print("Reading incoming data...")
                data = client_socket.recv(buffer_size)
                if not data:
                    print("Client disconnected")
                    break
                response = call_func(data, func_module)
                if response:
                    print("RESPONSE: ", response)
                    client_socket.sendall((response + "\n").encode("utf-8"))
    except KeyboardInterrupt:
        print("Shutting down Unix Socket...")
    finally:
        client_socket.close()  # Always close the client socket when done
        os.remove(socket_path)


def call_func(data, func_module):
    """
    Prepare input data and call function and return the result in JSON format
    or None.

    The response follows the next format:
    {
      "result": ... Result produced by the called function ...
    }
    """
    try:
        # Decode the data and parse the JSON
        message = data.decode('utf-8')
        json_data = json.loads(message)
        # Extract the fields from the JSON
        method_name = json_data.get("method_name", DEFAULT_METHOD_NAME)
        info = json_data.get("info")
        func_params = json_data.get("funcParams")
        print("method_name:", method_name)
        print("info:", info)
        print("funcs_params:", func_params)
    except json.JSONDecodeError:
        print("Received data is not valid JSON.")
        return

    print(f"Received method_name: {method_name}, info: {info}, "
          f"funcParams: {func_params}")
    # Call function
    if func_params:
        func = import_function(func_module, method_name)  # TODO: where is the method_name that I print in Java in UniXSocketClient?
        print(f"Running function: {func_module}.{func.__name__}")
        if func:
            # Compute function
            result = func(*func_params)
            # Wrap result in a JSON object and return
            result_json = json.dumps({"result": result})
            return result_json
        else:
            print(f"Warning: Method {method_name} not found in module.")
    else:
        print("Warning: No funcParams provided.")


if __name__ == "__main__":
    print("Received arguments: ", sys.argv)
    if len(sys.argv) < 4 or len(sys.argv) > 5:
        raise ValueError("Wrong number of arguments: need to pass"
                         " <socket_path> <func_module> <json_params>")
    try:
        main(*sys.argv[1:])
    except:
        print(f"Error: {traceback.print_exc()}")  # Allows showing the stack
