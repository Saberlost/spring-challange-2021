package com.lars;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class Cell {
    int index;
    int richess;
    int[] neighbours;
    int shade;
    Set<Integer> shadedFrom = new HashSet<>();
    Tree t;

    public Cell(int index, int richess, int[] neighbours) {
        this.index = index;
        this.richess = richess;
        this.neighbours = neighbours;
    }

    public boolean hasTree() {
        return t != null;
    }

    public void setTree(Tree t) {
        this.t = t;
    }

    public void shadeFrom(int i) {
        if (!shadedFrom.contains(i)) {
            shadedFrom.add(i);
            shade++;
        }

    }

    public void reset() {
        shadedFrom.clear();;
        shade = 0;
    }
}

class Tree {
    int cellIndex;
    int size;
    boolean isMine;
    boolean isDormant;
    long growScore = 0;

    public Tree(int cellIndex, int size, boolean isMine, boolean isDormant) {
        this.cellIndex = cellIndex;
        this.size = size;
        this.isMine = isMine;
        this.isDormant = isDormant;
    }
}

class Seed {
    int fromCellIndex;
    int toCellIndex;

    long seedScore;

    public Seed(int fromCellIndex, int toCellIndex, long seedScore) {
        this.fromCellIndex = fromCellIndex;
        this.toCellIndex = toCellIndex;
        this.seedScore = seedScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seed seed = (Seed) o;
        return toCellIndex == seed.toCellIndex &&
                seedScore == seed.seedScore;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toCellIndex, seedScore);
    }
}

class Action {
    static final String WAIT = "WAIT";
    static final String SEED = "SEED";
    static final String GROW = "GROW";
    static final String COMPLETE = "COMPLETE";


    static Action parse(String action) {
        String[] parts = action.split(" ");
        switch (parts[0]) {
            case WAIT:
                return new Action(WAIT);
            case SEED:
                return new Action(SEED, Integer.valueOf(parts[1]), Integer.valueOf(parts[2]));
            case GROW:
            case COMPLETE:
            default:
                return new Action(parts[0], Integer.valueOf(parts[1]));
        }
    }

    String type;
    Integer targetCellIdx;
    Integer sourceCellIdx;
    long score = -1;

    public Action(String type, Integer sourceCellIdx, Integer targetCellIdx) {
        this.type = type;
        this.targetCellIdx = targetCellIdx;
        this.sourceCellIdx = sourceCellIdx;
    }

    public Action(String type, Integer targetCellIdx) {
        this(type, null, targetCellIdx);
    }

    public Action(String type) {
        this(type, null, null);
    }

    @Override
    public String toString() {
        if (type == WAIT) {
            return Action.WAIT;
        }
        if (type == SEED) {
            return String.format("%s %d %d", SEED, sourceCellIdx, targetCellIdx);
        }
        return String.format("%s %d", type, targetCellIdx);
    }
}

class Game {

    static final int COMPLETE_COST = 4;
    static final int GROW_0_COST = 1;
    static final int GROW_1_COST = 3;
    static final int GROW_2_COST = 7;
    int day;
    int nutrients;
    List<Cell> board;
    List<Action> possibleActions;
    List<Tree> trees;
    int mySun, opponentSun;
    int myScore, opponentScore;
    boolean opponentIsWaiting;
    Action waitAction = new Action(Action.WAIT);
    long seedCost = 0;

    long badACtionScore = -2000;

    public Game() {
        waitAction.score = -1000;
        board = new ArrayList<>();
        possibleActions = new ArrayList<>();
        trees = new ArrayList<>();
        possibleActions.add(new Action(Action.WAIT));
    }

    Action getNextAction() {
        // TODO: write your algorithm here

        System.err.println("day is :"+day);

        board.forEach(Cell::reset);

        trees.forEach(t ->
        {
            Cell cell = board.get(t.cellIndex);
            for (int i = 0; i < 6; i++) {
                shadeTowards(cell, i, t.size);
            }
        });

        board.forEach(cell -> System.err.println(cell.shade));


        List<Tree> myTrees = trees.stream().filter(t -> t.isMine).collect(Collectors.toList());

        seedCost = myTrees.stream().filter(t -> t.size == 0).count();

        long grow0 = GROW_0_COST + myTrees.stream().filter(t -> t.size == 1).count();

        long grow1 = GROW_1_COST + myTrees.stream().filter(t -> t.size == 2).count();

        long grow2 = GROW_2_COST + myTrees.stream().filter(t -> t.size == 3).count();


        System.err.println("Grow cost 0 is " + grow0);

        System.err.println("MySun is  " + mySun);

        List<Tree> growTreesWithScore = trees.stream()
                .filter(t -> t.isMine)
                .filter(t -> !t.isDormant)
                .filter(t -> t.size < 3)
                .filter(t -> canGrow(t, grow0, grow1, grow2))
                .map(t -> growScore(t, grow0, grow1, grow2)).collect(Collectors.toList());

        List<Tree> treesThatCanBeCompleted = trees.stream()

                .filter(t -> !t.isDormant)
                .filter(t -> t.isMine)
                .filter(t -> t.size == 3)
                .filter(t -> mySun > COMPLETE_COST)
                .collect(Collectors.toList());

        List<Seed> seedsWithScore = trees.stream()
                .filter(t -> t.isMine)
                .filter(t -> t.size > 0)
                .filter(t -> seedCost <= mySun)
                .filter(t -> !t.isDormant)
                .flatMap(this::allPossibleSeeds)
                .distinct()
                .collect(Collectors.toList());

        Optional<Tree> growTree = growTreesWithScore.stream().reduce((t1, t2) -> t1.growScore > t2.growScore ? t1 : t2);

        Optional<Tree> completeTree = treesThatCanBeCompleted.stream().reduce((t1, t2) -> board.get(t1.cellIndex).richess > board.get(t2.cellIndex).richess ? t1 : t2);

        Optional<Seed> seed = seedsWithScore.stream().reduce((s1, s2) -> s1.seedScore > s2.seedScore ? s1 : s2);


        if(treesThatCanBeCompleted.size() >2){
            return  completeTree.map(t-> new Action(Action.COMPLETE, t.cellIndex)).orElse(waitAction);
        }




        Action bestScoreAction = Stream.of(
                growTree.map(t -> {
                    Action action = new Action(Action.GROW, t.cellIndex);
                    action.score = t.growScore;
                    return action;
                }).orElse(waitAction),
                completeTree.map(t -> {
                    Action action = new Action(Action.COMPLETE, t.cellIndex);
                    action.score = board.get(t.cellIndex).richess * 4 +  nutrients - ((23-day) *(6-board.get(t.cellIndex).shade));
                    return action;
                }).orElse(waitAction),

                seed.map(s -> {
                    Action action = new Action(Action.SEED, s.fromCellIndex, s.toCellIndex);
                    action.score = s.seedScore;
                    return action;

                }).orElse(waitAction)
        ).reduce((a1, a2) -> a1.score > a2.score ? a1 : a2).orElse(waitAction);

        return bestScoreAction;

        //return oldGetAction();
        //return possibleActions.get();
    }


    private void shadeTowards(Cell cell, int i, int size) {
        if (size > 0) {
            if (cell.neighbours[i] >= 0) {
                board.get(cell.neighbours[i]).shadeFrom(i);
                size--;
                shadeTowards(board.get(cell.neighbours[i]), i, size);
            }
        }
    }


    private Stream<Seed> allPossibleSeeds(Tree tree) {
        Stream<Cell> spaces = Arrays.stream(board.get(tree.cellIndex).neighbours)
                .filter(c -> c >= 0)

                .mapToObj(c -> board.get(c));
        if (tree.size > 1) {
            spaces = spaces.flatMapToInt(c -> Arrays.stream(c.neighbours))
                    .filter(c -> c >= 0)
                    .mapToObj(c -> board.get(c));

        }
        return spaces.distinct().filter(cell -> !cell.hasTree()).filter(c -> c.richess > 0).map(c -> calculateSeedScore(c, tree));

    }

    private boolean canGrow(Tree t, long grow0, long grow1, long grow2) {
        if (t.size == 0) {
            return mySun >= grow0;
        }
        if (t.size == 1) {
            return mySun >= grow1;
        } else {
            return mySun >= grow2;
        }
    }

    private Seed calculateSeedScore(Cell cell, Tree tree) {
        //Make more seeds not good
        long seedScore = (cell.richess*3) - seedCost*2 + getShadeBonus(cell);
        if(day > 19)
            seedScore = badACtionScore;
        return new Seed(tree.cellIndex, cell.index, seedScore);
    }

    private int getShadeBonus(Cell cell) {
        return ((6-cell.shade) * (23-day)/2)/3;
    }

    private Tree growScore(Tree t, long grow0, long grow1, long grow2) {
        long growCostScore = 0;
        if (t.size == 0) {
            growCostScore = grow0;
            if(day > 19)
                growCostScore -= badACtionScore;


        }else if (t.size == 1) {
            growCostScore = grow1;
            if(day > 20)
                growCostScore -= badACtionScore;
        } else {

            growCostScore = grow2;
            if(day > 22)
                growCostScore -= badACtionScore;
        }
        Cell cell = board.get(t.cellIndex);
        t.growScore = cell.richess * 4 * (t.size + 1) - growCostScore + (getShadeBonus(cell))*(t.size + 1);
        return t;
    }

}

class Player {
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);

        Game game = new Game();

        int numberOfCells = in.nextInt();
        for (int i = 0; i < numberOfCells; i++) {
            int index = in.nextInt();
            int richness = in.nextInt();
            int neigh0 = in.nextInt();
            int neigh1 = in.nextInt();
            int neigh2 = in.nextInt();
            int neigh3 = in.nextInt();
            int neigh4 = in.nextInt();
            int neigh5 = in.nextInt();
            int neighs[] = new int[]{neigh0, neigh1, neigh2, neigh3, neigh4, neigh5};
            Cell cell = new Cell(index, richness, neighs);
            game.board.add(cell);
        }

        while (true) {
            game.board.forEach(c -> c.setTree(null));
            game.day = in.nextInt();
            game.nutrients = in.nextInt();
            game.mySun = in.nextInt();
            game.myScore = in.nextInt();
            game.opponentSun = in.nextInt();
            game.opponentScore = in.nextInt();
            game.opponentIsWaiting = in.nextInt() != 0;

            game.trees.clear();
            int numberOfTrees = in.nextInt();
            for (int i = 0; i < numberOfTrees; i++) {
                int cellIndex = in.nextInt();
                int size = in.nextInt();
                boolean isMine = in.nextInt() != 0;
                boolean isDormant = in.nextInt() != 0;
                Tree tree = new Tree(cellIndex, size, isMine, isDormant);
                game.trees.add(tree);
                game.board.get(tree.cellIndex).setTree(tree);
            }

            game.possibleActions.clear();
            int numberOfPossibleActions = in.nextInt();
            in.nextLine();
            for (int i = 0; i < numberOfPossibleActions; i++) {
                String possibleAction = in.nextLine();
                game.possibleActions.add(Action.parse(possibleAction));
            }


            Action action = game.getNextAction();
            System.out.println(action);
        }
    }
}