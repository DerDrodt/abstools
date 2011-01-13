package abs.backend.java.debugging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import abs.backend.java.lib.runtime.ABSException;
import abs.backend.java.observing.COGView;
import abs.backend.java.observing.FutView;
import abs.backend.java.observing.GuardView;
import abs.backend.java.observing.ObjectView;
import abs.backend.java.observing.TaskObserver;
import abs.backend.java.observing.TaskSchedulerObserver;
import abs.backend.java.observing.TaskView;

public class DebugModel implements TaskObserver, TaskSchedulerObserver {
    final Map<TaskView, TaskInfo> taskToLineMap = new HashMap<TaskView, TaskInfo>();
    final Map<COGView, COGInfo> cogInfo = new HashMap<COGView, COGInfo>();
    final ArrayList<DebugModelListener> listener = new ArrayList<DebugModelListener>();
    final Set<TaskView> steppingTasks = new HashSet<TaskView>();

    public COGInfo getCOGInfo(COGView view) {
        return cogInfo.get(view);
    }

    public synchronized TaskInfo getTaskInfo(TaskView task) {
        return taskToLineMap.get(task);
    }

    public synchronized Semaphore getSema(TaskView info) {
        return taskToLineMap.get(info).stepSema;
    }

    public void stepTask(TaskView task) {
        getSema(task).release();
    }

    public void cogCreated(COGView cog, ObjectView initialObject) {
        cog.getScheduler().registerTaskSchedulerObserver(this);
        ArrayList<DebugModelListener> localList;
        COGInfo info = new COGInfo(cog, initialObject);
        synchronized (this) {
            cogInfo.put(cog, info);
            localList = new ArrayList<DebugModelListener>(listener);
        }

        for (DebugModelListener l : localList) {
            l.cogCreated(info);
        }
    }

    public synchronized TaskInfo addInfoLine(TaskView task) {
        TaskInfo line = new TaskInfo(task);
        taskToLineMap.put(task, line);
        for (DebugModelListener l : listener) {
            l.taskInfoAdded(line);
        }
        return line;
    }

    public void updateInfoLine(TaskView task, TaskInfo line) {
        ArrayList<DebugModelListener> localList;
        synchronized (this) {
            taskToLineMap.put(task, line);
            localList = new ArrayList<DebugModelListener>(listener);
        }

        for (DebugModelListener l : localList) {
            l.taskInfoChanged(line);
        }
    }

    public synchronized void removeInfoLine(TaskView task) {
        TaskInfo line = taskToLineMap.remove(task);
        for (DebugModelListener l : listener) {
            l.taskInfoRemoved(line);
        }
    }

    public synchronized void registerListener(DebugModelListener l) {
        listener.add(l);
    }

    @Override
    public synchronized void taskCreated(TaskView task) {
        steppingTasks.add(task);

        task.registerTaskListener(this);
        TaskInfo info = addInfoLine(task);

        COGInfo cinfo = cogInfo.get(task.getCOG());
        cinfo.addTask(info);
        cogInfoChanged(cinfo);
    }

    private synchronized void cogInfoChanged(COGInfo info) {
        for (DebugModelListener l : listener) {
            l.cogChanged(info);
        }
    }

    @Override
    public synchronized void taskSuspended(TaskView task, GuardView guard) {
        updateTaskState(task, TaskState.SUSPENDED, guard, null);
    }

    @Override
    public synchronized void taskStarted(TaskView task) {
        updateTaskState(task, TaskState.RUNNING, null, null);
    }

    @Override
    public synchronized void taskDeadlocked(TaskView task) {
        updateTaskState(task, TaskState.DEADLOCKED, null, null);
    }

    @Override
    public synchronized void taskFinished(TaskView task) {
        TaskState newState = TaskState.FINISHED;

        if (task.hasException()) {
            ABSException e = task.getException();
            if (e.isAssertion()) {
                newState = TaskState.ASSERTION_FAILED;
            } else {
                newState = TaskState.EXCEPTION;
            }
        }

        updateTaskState(task, newState, null, null);
    }

    @Override
    public synchronized void taskBlockedOnFuture(TaskView task, FutView fut) {
        updateTaskState(task, TaskState.BLOCKED, null, fut);
    }

    @Override
    public void taskRunningAfterWaiting(TaskView task, FutView fut) {
        updateTaskState(task, TaskState.RUNNING, null, null);
    }

    @Override
    public void taskResumed(TaskView task, GuardView view) {
        updateTaskState(task, TaskState.RUNNING, null, null);
    }

    @Override
    public void taskReady(TaskView task) {
        updateTaskState(task, TaskState.READY, null, null);
    }

    private void updateTaskState(TaskView task, TaskState state, GuardView guard, FutView fut) {
        synchronized (this) {
            TaskInfo info = getTaskInfo(task);
            if (state == TaskState.RUNNING) {
                if (info.isStepping) {
                    steppingTasks.add(task);
                }
            } else {
                steppingTasks.remove(task);
            }
            info.state = state;
            info.waitingOnGuard = guard;
            info.blockedOnFuture = fut;
            updateInfoLine(task, info);
        }
    }

    private void waitForClick(TaskView task) {
        try {
            System.out.println("Task " + task.getID() + " waiting for click...");
            if (getTaskInfo(task).isStepping)
                getSema(task).acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void taskStep(TaskView task, String fileName, int line) {
        synchronized (this) {
            TaskInfo info = getTaskInfo(task);
            info.updateLine(line);
            info.updateFile(fileName);
            info.state = TaskState.RUNNING;
            updateInfoLine(task, info);
        }
        // waitForClick(task);
    }

    public synchronized void stepRandom() {
        stepTask(steppingTasks.iterator().next());
    }

    public synchronized void runTask(TaskView task) {
        steppingTasks.remove(task);
        getTaskInfo(task).isStepping = false;
        getSema(task).release();
    }
    
    //New Methods
    public List<COGView> getCOGs(){
        List<COGView> cogs = new ArrayList<COGView>();
        cogs.addAll(cogInfo.keySet());
        return cogs;
    }
    
    public List<COGInfo> getCOGInfos(){
        List<COGInfo> cogInfos = new ArrayList<COGInfo>();
        for(COGView cog : getCOGs()){
            cogInfos.add(cogInfo.get(cog));
        }
        return cogInfos;
    }
    
    public List<TaskInfo> getTaskInfos(COGView cog){
        return cogInfo.get(cog).getTasks();
    }
    
    public List<TaskView> getTasks(COGView cog){
        List<TaskView> tasks = new ArrayList<TaskView>();
        for(TaskInfo taskInfo : getTaskInfos(cog)){
            tasks.add(taskInfo.getTaskView());
        }
        return tasks;
    }

}