class Schedule:
    def __init__(self):
        self.master_schedule = {}
        self.list_of_queries = {'1': [], '2': []}
        self.batch_size = 1 # Hardcoded for now

    def add_new_query(self, model_type, file_name):
        self.list_of_queries[model_type].append(file_name)
        self.master_schedule[file_name] = Query(file_name, model_type)

    def print_schedule(self):
        for i in self.master_schedule:
            print(f"Test data filename = {self.master_schedule[i].file_name}\t\tModel type = {self.master_schedule[i].model_type}\t\tResult = {self.master_schedule[i].result}\t\tElapsed time = {self.master_schedule[i].elapsed_time}\t\tStart time = {self.master_schedule[i].start_time}\t\tStop time = {self.master_schedule[i].stop_time}\t\tAssigned VM = {self.master_schedule[i].assigned_vm}")

    def update_assigned_vm(self, key, assigned_vm):
        self.master_schedule[key].assigned_vm = assigned_vm

    def update_status(self, key, status):
        self.master_schedule[key].status = status

    def update_result(self, key, result):
        self.master_schedule[key].result = result

    def update_start_time(self, key, start_time):
        self.master_schedule[key].start_time = start_time

    def update_stop_time(self, key, stop_time):
        self.master_schedule[key].stop_time = stop_time

    def update_elapsed_time(self, key):
        self.master_schedule[key].elapsed_time = self.master_schedule[key].stop_time - self.master_schedule[key].start_time

    def get_total_num_of_queries(self):
        return len(self.master_schedule)

class Query:
    def __init__(self, file_name, model_type):
        self.model_type = model_type # Can be either 1 or 2
        self.file_name = file_name # Name of test data file
        self.assigned_vm = "" # VM assigned to complete the query
        self.status = "unscheduled" # One of {"ongoing", "completed", "failed"}
        self.result = "" # ML model result
        self.start_time = 0 
        self.stop_time = 0
        self.elapsed_time = 0 # Effectively query time???
