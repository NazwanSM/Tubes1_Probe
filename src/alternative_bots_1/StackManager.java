package alternative_bots_1;

public class StackManager {

    private final Task[] stack;
    private int top;

    public StackManager(Task defaultTask) {
        stack = new Task[8];
        top = 0;
        stack[0] = defaultTask;
    }

    public Task currentTask() {
        return stack[top];
    }

    public void push(Task task) {
        if (stack[top] == task) return;
        if (top < stack.length - 1) {
            stack[++top] = task;
        }
    }

    public Task pop() {
        if (top > 0) return stack[top--];
        return stack[0];
    }

    public boolean contains(Task task) {
        for (int i = top; i >= 0; i--) {
            if (stack[i] == task) return true;
        }
        return false;
    }

    public int size() { return top + 1; }

    public void reset(Task task) {
        top = 0;
        stack[0] = task;
    }
}
