package jwp.fuzz;

public class BranchTree {
    public BranchTreeNode root;

    public class BranchTreeNode {
        public BranchTreeNode success = null;
        public BranchTreeNode fail = null;
        public String ident = null;
    }

}



