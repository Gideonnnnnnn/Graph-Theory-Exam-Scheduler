import java.util.*;
import java.io.*;

public class Run {
    public static void main(String[] args) {
        try {
            ExamScheduler scheduler = new ExamScheduler();

            // Configure term filter and capacity enforcement from command line arguments
            // Usage: java Run [term] [strictCapacity]
            // Examples:
            // java Run 202440 - Process Fall 2024, flexible capacity
            // java Run 202440 true - Process Fall 2024, strict 1500-seat limit
            // java Run 202440 false - Process Fall 2024, minimize time slots

            String term = null;
            boolean strictCapacity = false;

            if (args.length > 0) {
                term = args[0];
                scheduler.setTermFilter(term);
            }

            if (args.length > 1) {
                strictCapacity = Boolean.parseBoolean(args[1]);
                scheduler.setStrictCapacityEnforcement(strictCapacity);
            }

            System.out.println("==================================");
            System.out.println("Final Exam Scheduler (DSATUR + Kempe)");
            if (term != null) {
                System.out.println("Processing term: " + term);
            } else {
                System.out.println("Processing all terms");
            }
            System.out.println("Capacity enforcement: "
                    + (strictCapacity ? "STRICT (max 1500 seats/slot)" : "FLEXIBLE (minimize slots)"));
            System.out.println("==================================\n");

            // Set Kempe chain time budget (in milliseconds)
            scheduler.setKempeBudgetMs(1000);

            // Build the conflict graph from CSV files
            System.out.println("Building conflict graph from CSV files...");
            scheduler.createGraph();

            System.out.println("\nGraph statistics:");
            System.out.println("  Courses (vertices): " + scheduler.getGraph().getVertexCount());
            System.out.println("  Conflicts (edges): " + scheduler.getGraph().getEdgeCount());

            // Run the coloring algorithm
            System.out.println("\nRunning DSATUR + Kempe chain coloring algorithm...");
            long startTime = System.currentTimeMillis();
            scheduler.findMinimumColoringOnceAndValidate();
            long endTime = System.currentTimeMillis();

            // Display results
            System.out.println("\n==================================");
            System.out.println("RESULTS");
            System.out.println("==================================");
            System.out.println("Number of time slots needed: " + scheduler.getNumTimeSlots());
            System.out.println("Time taken: " + (endTime - startTime) + " ms");
            System.out.println("Valid coloring: " + scheduler.isValidColoring());

            // Show exam schedule
            System.out.println("\n==================================");
            System.out.println("EXAM SCHEDULE");
            System.out.println("==================================");
            Map<String, List<String>> schedule = scheduler.getExamSchedule();
            Map<String, Integer> courseEnrollments = scheduler.getCourseEnrollments();

            // Get sorted time slots
            List<String> timeSlots = new ArrayList<>(schedule.keySet());
            Collections.sort(timeSlots, (a, b) -> {
                int slotA = Integer.parseInt(a.replaceAll("[^0-9]", ""));
                int slotB = Integer.parseInt(b.replaceAll("[^0-9]", ""));
                return Integer.compare(slotA, slotB);
            });

            for (String timeSlot : timeSlots) {
                List<String> courses = schedule.get(timeSlot);
                int totalSeats = 0;
                for (String course : courses) {
                    totalSeats += courseEnrollments.getOrDefault(course, 1);
                }

                System.out.println("\n" + timeSlot + " (" + courses.size() + " courses, " + totalSeats + " seats)");
                if (totalSeats > 1500) {
                    System.out.println("  ⚠️  WARNING: Exceeds 1500 seat capacity limit!");
                }

                // Show first 10 courses, then summarize
                if (courses.size() <= 10) {
                    for (String course : courses) {
                        System.out.println("  - " + course);
                    }
                } else {
                    for (int i = 0; i < 10; i++) {
                        System.out.println("  - " + courses.get(i));
                    }
                    System.out.println("  ... and " + (courses.size() - 10) + " more courses");
                }
            }

            // Validate seating capacity constraint
            System.out.println("\n==================================");
            System.out.println("CAPACITY VALIDATION");
            System.out.println("==================================");
            System.out.println("Maximum seats per slot: 1500");
            boolean capacityValid = true;
            for (Map.Entry<String, List<String>> entry : schedule.entrySet()) {
                int totalSeats = 0;
                for (String course : entry.getValue()) {
                    totalSeats += courseEnrollments.getOrDefault(course, 1);
                }
                if (totalSeats > 1500) {
                    capacityValid = false;
                }
            }
            System.out.println("Seating capacity constraint satisfied: " + capacityValid);

            // Print color->day mapping
            System.out.println("\n==================================");
            System.out.println("CALENDAR DAY MAPPING");
            System.out.println("==================================");
            System.out.println("(Max 2 exams per student per day)");
            Map<Integer, Integer> c2d = scheduler.getColorToDayMapping();
            List<Integer> colors = new ArrayList<>(c2d.keySet());
            Collections.sort(colors);
            int maxDay = 0;
            for (Integer c : colors) {
                int day = c2d.get(c);
                System.out.println("  Time Slot " + (c + 1) + " -> Calendar Day " + (day + 1));
                if (day > maxDay)
                    maxDay = day;
            }
            System.out.println("\nTotal calendar days needed: " + (maxDay + 1));

            System.out.println("\n==================================");
            System.out.println("Schedule generation complete!");
            System.out.println("==================================");

        } catch (Exception e) {
            System.err.println("Error running exam scheduler:");
            e.printStackTrace();
        }
    }
}
