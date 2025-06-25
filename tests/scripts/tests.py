import subprocess
import os


def execute_script(script_path):
    print(f"Running: {script_path}")
    try:
        if script_path.endswith('.sh'):
            subprocess.run(['bash', script_path], check=True)
        elif script_path.endswith('.py'):
            subprocess.run(['python3', script_path], check=True)
        else:
            print(f"Skipped (unsupported extension): {script_path}")
    except subprocess.CalledProcessError as e:
        print(f"Error running {script_path}: {e}")
        exit(1)

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    sources_dir = os.path.join(script_dir, '..', 'sources')
    for root, _, files in os.walk(sources_dir):
        for file in files:
            if file.endswith(('.sh', '.py')):
                execute_script(os.path.join(root, file))

if __name__ == "__main__":
    main()