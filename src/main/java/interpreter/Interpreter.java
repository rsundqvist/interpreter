package interpreter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import contract.json.Operation;
import contract.operation.OP_ReadWrite;
import contract.operation.OP_Swap;
import contract.operation.OP_Write;
import contract.operation.OperationType;
import contract.utility.OpUtil;

/**
 * An Interpreter attempts to increase the abstraction level of operation logs be
 * consolidating read and write operations into higher level operations.
 *
 * @author Richard Sundqvist
 *
 */
public class Interpreter {

    // ============================================================= //
    /*
     *
     * Field variables
     *
     */
    // ============================================================= //

    private final LinkedList<Operation>    before;
    private final LinkedList<Operation>    after;
    /**
     * The group of operations currently being evaluated.
     */
    private final LinkedList<OP_ReadWrite> workingSet;
    private final Consolidator             consolidator;

    /**
     * Try to expend the current working set. Messages are immediately added to high level
     * operations.
     */
    private Operation                      candidate;

    // ============================================================= //
    /*
     *
     * Field variables
     *
     */
    // ============================================================= //

    /**
     * Create a new Interpreter.
     */
    public Interpreter () {
        before = new LinkedList<Operation>();
        after = new LinkedList<Operation>();
        workingSet = new LinkedList<OP_ReadWrite>();
        consolidator = new Consolidator();
    }

    // ============================================================= //
    /*
     *
     * Control
     *
     */
    // ============================================================= //

    /**
     * Interpret the operations contained in {@code candidateList}. The list provided as
     * argument will not be changed.
     *
     * @param candidateList
     *            The list to interpret.
     * @return A list of interpreted operations.
     */
    public List<Operation> interpret (List<Operation> candidateList) {
        // Clean up data from previous executions.
        before.clear();
        after.clear();
        workingSet.clear();

        // Set before list and begin.
        before.addAll(candidateList);
        interpret();
        
        // Return the result.
        return new ArrayList<Operation>(after);
    }

    // ============================================================= //
    /*
     *
     * Getters and Setters
     *
     */
    // ============================================================= //

    /**
     * Add a test case to the Interpreter.
     *
     * @param testCase
     *            The test case to add.
     */
    public void addTestCase (OperationType testCase) {
        switch (testCase) {
        case swap:
            consolidator.addConsolidable(new OP_Swap());
            break;
        default:
            System.err.print("Cannot consolidate OperationType: " + testCase.toString().toUpperCase());
            break;
        }
    }

    /**
     * Remove a given testCase. When this method returns, the testcase is guaranteed to be
     * removed.
     *
     * @param testCase
     *            The testcase to remove.
     */
    public void removeTestCase (OperationType testCase) {
        switch (testCase) {
        case swap:
            consolidator.removeTestCase(testCase, testCase.numAtomicOperations);
            break;
        default:
            System.err.print("Unknown Consolidable type: " + testCase);
            break;
        }
    }

    /**
     * Returns a list of all active test cases for this Consolidator.
     *
     * @return A list of all active test cases for this Consolidator.
     */
    public List<OperationType> getTestCases () {
        return consolidator.getTestCases();
    }

    /**
     * Returns the Consolidator used by the interpreter.
     *
     * @return A Consolidator.
     */
    public Consolidator getConsolidator () {
        return consolidator;
    }

    // ============================================================= //
    /*
     *
     * Utility
     *
     */
    // ============================================================= //

    /**
     * Build and filter working sets until all operations in {@code before} have been
     * processed. When this method returns, {@code before.size()} will be 0.
     */
    private void interpret () {
        int minWorkingSetSize = consolidator.getMinimumSetSize();
        int maxWorkingSetSize = consolidator.getMaximumSetSize();
        
        if (minWorkingSetSize < 0 || maxWorkingSetSize < 0) {
            after.addAll(before);
            return; // No operations in Consolidator.
        }
        
        // Continue until all operations are handled
        outer: while (before.isEmpty() == false || workingSet.isEmpty() == false) {

            while (workingSet.size() < minWorkingSetSize) {
                if (tryExpandWorkingSet() == false) {
                    break outer;
                }
            }
            
            // Expand working set and attempt consolidation.
            while (workingSet.size() <= maxWorkingSetSize) {
                if (consolidateWorkingSet() == true) {
                    workingSet.clear();
                    continue outer; // Working set converted to a more complex
                                    // operation. Begin work on new set.
                }
                if (tryExpandWorkingSet() == false) {
                    break outer;
                }
            }
            // Add the first operation of working set to consolidated
            // operations.
            after.add(workingSet.removeFirst());

            // Reduce the working set.
            while (workingSet.size() > minWorkingSetSize) {
                reduceWorkingSet();
            }
        }
        after.addAll(workingSet);
    }

    /**
     * Reduce the size of the working set by removing the last element and adding it first
     * to the list of low level operations.
     */
    private void reduceWorkingSet () {
        // Add the last element of working set to the first position in low
        // level operations.
        before.addFirst(workingSet.removeLast());
    }

    private boolean tryExpandWorkingSet () {
        if (before.isEmpty()) {
            return false;
        }
        candidate = before.remove(0);
        if (candidate.operation == OperationType.message) {
            keepSetAddCandidate();
            return tryExpandWorkingSet();
        } else if (candidate.operation == OperationType.write) {
            OP_Write write_candidate = (OP_Write) candidate;
            if (write_candidate.getValue().length > 1) {
                flushSetAddCandidate();
                return tryExpandWorkingSet();
            }
        }
        if (!OpUtil.isReadOrWrite(candidate)) {
            flushSetAddCandidate();
            return tryExpandWorkingSet();
        }
        // Add the read/write operation to the working set.
        workingSet.add((OP_ReadWrite) candidate);
        return true;
    }

    /**
     * Add high-level operation found to processedOperations, then continue on the current
     * working set.
     */
    private void keepSetAddCandidate () {
        after.add(candidate);
    }

    /**
     * Flush the working set into processedOperations, then add the high-level operation
     * as well.
     */
    private void flushSetAddCandidate () {
        after.addAll(workingSet);
        after.add(candidate);
        workingSet.clear();
    }

    /**
     * Attempt to consolidate the working set held by this Interpreter. Will return true
     * and add the new operation to processedOperations if successful. Will not clear the
     * working set.
     *
     * @return True if workingSet was successfully consolidated, false otherwise.
     */
    private boolean consolidateWorkingSet () {
        Operation consolidatedOperation = consolidator.attemptConsolidate(workingSet);

        if (consolidatedOperation != null) {
            after.add(consolidatedOperation);
            return true;
        }
        return false;
    }
}
