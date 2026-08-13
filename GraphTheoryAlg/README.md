# Graph-Theory-Exam-Scheduler
Lehigh University Graph Theory Research Group, Dr. Meghan Cream, Adam Wax, Gideon Sobek, Molly Pecic

This project was developed by an informal group of students and a professor in the Mathematics department. 
It was formed to examine suboptimal exam scheduling procedures using university data and Graph-Coloring algorithms. 

Exam timetabling is subject to many hard and soft restraints, namely that two students in the same class cannot have exams for other classes scheduling simultaneous to the primary class' exam. This implies a discrete network where vertices are courses (and exams) and edges are students enrolled in both vertices. Other restrains include the size and number of seats of a classroom, frequency of exams per day and week, weekend breaks, and minimizing total exam period length in days. 

The combinatorics of this problem are difficult and have attracted sustained attention for competitions and practical purposes. Additionally, using a graph coloring approacd increases the difficulty of the issue. Discovering the Chromatic Number of a Graph is NP-hard, but many heuristics exist, including Saturation Degree (DSATUR / SD) and Recursive Largest First (RLF). 

## Goals
While not contributing to the literature, our goals were to demonstrate a more efficient scheduling for Lehigh final exams using a grpah coloring algorithm. We develop a hybrid technique which incorporates the DSATUR heuristic and Kempe Chain local optimization. DSATUR constructs an initial coloring by prioritizing highest-degree vertices, and then the algorithm iteratively refines the coloring via Kempe optimization. The coloring thus approaches the chromatic number. 

##Format
The scheduler processes two CSV files containing institutional enrollment data:

Individual Enrollment Courses With 10+.csv contains individual student enrollments for courses with 10 or more students.

**Format:**

```
AnonID,Parent_CRN,Term,Parent_Crosslist Code,Parent Crosslist Course(s),Parent_Course,Parent_Title
```

**Example:**

```
ANON-709304120,41919,202440,TR,AAS 066-010 (41921),THTR 066-010,Hip Hop Dance
```

Overlap Student Counts in Courses Under 10.csv contains pairwise overlap counts for smaller courses (under 10 students).

**Format:**

```
LTerm,LParent_CRN,LParent_Title,LParent_Course,LParent_Crosslist Code,LParent Crosslist Course(s),SHARED_STUDENTS,RParent_CRN,RParent_Title,RParent_Course,RParent_Crosslist Code,RParent Crosslist Course(s)
```

