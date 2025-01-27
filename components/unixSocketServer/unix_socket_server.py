import sys
import socket
import json
import importlib.util
import os
import traceback


# Load funcs directory
curr_module_path = os.path.abspath(__file__)
print(curr_module_path.split(os.sep)[:-1])
curr_dir_path = os.sep.join(curr_module_path.split(os.sep)[:-1])
print("CURR DIR PATH: ", curr_dir_path)
sys.path.append(os.path.join(curr_dir_path, "funcs"))
print("CURRENT PATH:", curr_dir_path)
print("PATH ENV:", sys.path)


def import_function(module_name, function_name):
    module = importlib.import_module(module_name)
    return getattr(module, function_name)


def main(socket_path, func_module, json_params, buffer_size=1024):
    print("Instantiating socket", socket_path, flush=True)
    print("func_module:", func_module)
    print("json_params:", json_params)
    print("buffer_size:", buffer_size)
    
    server_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    print("1111111111111")
    # Clean up existing socket if present
    try:
        os.unlink(socket_path)
    except OSError:
        pass
    server_socket.bind(socket_path)
    print("2222222222222")
    server_socket.listen(1)
    print("333333333333333")

    print(f"Server listening on {socket_path}")

    while True:
        client_socket, _ = server_socket.accept()

        try:
            print("Reading incoming data...")
            data = client_socket.recv(buffer_size)  # Read incoming data from the client
            if not data:
                continue

            # Decode the data and parse the JSON
            message = data.decode('utf-8')
            json_data = json.loads(message)

            # Extract the fields from the JSON
            default_method_name = "main"  # Use main by default
            method_name = json_data.get("method_name", default_method_name)
            info = json_data.get("info")
            func_params = json_data.get("funcParams")

            print(f"Received method_name: {method_name}, info: {info}, funcParams: {func_params}")

            # Assuming you have a function you want to call in func_module
            if func_params:
                func = import_function(func_module, method_name)  # TODO: where is the method_name that I print in Java in UniXSocketClient?
                
                # Verify contents of the module:
                print(func)
                if func:
                    result = func(*func_params)  # Call the method with funcParams
                    print(f"Result: {result}")
                else:
                    print(f"Method {method_name} not found in module.")
            else:
                print("No funcParams provided.")
            
        except json.JSONDecodeError:
            print("Received data is not valid JSON.")
        except Exception as e:
            print(f"Error: {traceback.print_exc()}")
        finally:
            client_socket.close()  # Always close the client socket when done


if __name__ == "__main__":
    print("Received arguments: ", sys.argv)
    if len(sys.argv) < 4 or len(sys.argv) > 5:
        raise ValueError("Wrong number of arguments: need to pass <socket_path> <func_module> <json_params>")
    main(*sys.argv[1:])
