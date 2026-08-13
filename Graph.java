import java.util.*;

public class Graph {
    private Map<String, Set<String>> adjacencyList;

    /**
     * constructs an empty graph
     */
    public Graph() {
        adjacencyList = new HashMap<>();
    }

    /**
     * adds a vertex to the graph
     * 
     * @param vertex the vertex to add
     */
    public void addVertex(String vertex) {
        if (!adjacencyList.containsKey(vertex)) {
            adjacencyList.put(vertex, new HashSet<>());
        }
    }

    /**
     * adds an edge between two vertices
     * 
     * @param vertex1 first vertex
     * @param vertex2 second vertex
     */
    public void addEdge(String vertex1, String vertex2) {
        // check if both already exist or not
        addVertex(vertex1);
        addVertex(vertex2);

        // add the edge in both directions
        adjacencyList.get(vertex1).add(vertex2);
        adjacencyList.get(vertex2).add(vertex1);
    }

    /**
     * get all neighbors of a vertex
     * 
     * @param vertex the vertex
     * @return a set of the neighboring vertices
     */
    public Set<String> getNeighbors(String vertex) {
        return adjacencyList.getOrDefault(vertex, new HashSet<>());
    }

    /**
     * get all vertices
     * 
     * @return a set of all the vertices
     */
    public Set<String> getVertices() {
        return adjacencyList.keySet();
    }

    /**
     * get the number of adjacent vertices
     * 
     * @param vertex the vertex
     * @return the degree of the vertex
     */
    public int getDegree(String vertex) {
        return getNeighbors(vertex).size();
    }

    /**
     * check if the graph is empty
     * 
     * @return true if the graph has no vertices, false otherwise
     */
    public boolean isEmpty() {
        return adjacencyList.isEmpty();
    }

    /**
     * get the number of vertices in the graph
     * 
     * @return the number of vertices
     */
    public int getVertexCount() {
        return adjacencyList.size();
    }

    /**
     * get the number of edges in the graph
     * 
     * @return the # of edges
     */
    public int getEdgeCount() {
        int count = 0;
        for (Set<String> neighbors : adjacencyList.values()) {
            count += neighbors.size();
        }
        // double counted every edge, so divide by 2
        return count / 2;
    }

    /**
     * checks if two vertices share an edge
     * 
     * @param vertex1 the first vertex
     * @param vertex2 the second vertex
     * @return true if the vertices are adjacent, false if not
     */
    public boolean areAdjacent(String vertex1, String vertex2) {
        if (!adjacencyList.containsKey(vertex1) || !adjacencyList.containsKey(vertex2)) {
            return false;
        }
        return adjacencyList.get(vertex1).contains(vertex2);
    }

    /**
     * print out the graph
     * 
     * @return the graph's visual representation
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph with ").append(getVertexCount()).append(" vertices and ")
                .append(getEdgeCount()).append(" edges:\n");
        return sb.toString();
    }
}
