module load COMPSs/Trunk
module load python/3.9.10

enqueue_compss \
--pythonpath="/path/to/root/hp2c-dt/tests":"/path/to/root/hp2c-dt/source"  \
--num_nodes=2 \
--worker_working_dir=local_disk \
--master_working_dir=local_disk \
--lang=python \
--qos=debug \
--exec_time=2 \
convergenceTest.py

