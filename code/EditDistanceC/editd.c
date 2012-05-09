#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/time.h>
time_t time ( time_t * timer );

#ifndef max
    #define max(a, b) ( ((a) > (b)) ? (a) : (b) )
#endif

#ifndef min
    #define min(a, b) ( ((a) < (b)) ? (a) : (b) )
#endif

/* The editd struct represents an edit distance problem.
 * 
 * max_str: the longer of the two strings
 * max_len: the number of characters in max_str
 * min_str: the shorter of the two strings
 * min_len: the number of characters in min_str
 */
typedef struct {
    int max_len;
    int min_len;
    char *max_str;
    char *min_str;
} editd;

/* editd_create initializes the fields of the editd struct.
 */
void editd_create(editd *prob, char *s1, char *s2) {
    char *max_str, *min_str;
    int max_len, min_len;

    int s1_len = strlen(s1);
    int s2_len = strlen(s2);
    if (s1_len > s2_len) {
        max_len = s1_len;
        min_len = s2_len;
        max_str = s1;
        min_str = s2;
    } else {
        max_len = s2_len;
        min_len = s1_len;
        max_str = s2;
        min_str = s1;
    }
    prob->max_str = max_str;
    prob->min_str = min_str;
    prob->max_len = max_len;
    prob->min_len = min_len;
}

/* editd_penalty compares max_str at index x to min_str at
 * index y. It returns 0 if the two chars match, and 0 otherwise.
 */
int editd_penalty(editd *prob, int x, int y) {
    return (prob->max_str[x] == prob->min_str[y]) ? 0 : 1;
}

/* editd_worker fills in column using the edit distance algorithm.
 * 
 * prev: the column in the edit distance table before column (the argument)
 * column: the column to fill in the edit distance table
 * iteration: the index of the column in the table
 */
void editd_worker(editd *prob, int *prev, int *column, int iteration) {
    int left = 1 + prev[0];
    int down = 2 + iteration;
    int diagonal = editd_penalty(prob, iteration, 0) + iteration;
    column[0] = min(left, min(down, diagonal));
    for (int i = 1; i < prob->min_len; i++) {
        left = 1 + prev[i];
        down = 1 + column[i - 1];
        diagonal = editd_penalty(prob, iteration, i) + prev[i - 1];
        column[i] = min(left, min(down, diagonal));
    }
}

/* editd_base_case is used to fill in the first column of the edit
 * distance table. It assumes there is no previous column, so the base
 * cases are used for all left and diagonal values. 
 * 
 * column: the first column in the edit distance table
 * 
 */
void editd_base_case(editd *prob, int* column) {
    column[0] = editd_penalty(prob, 0, 0);
    for (int i = 1; i < prob->min_len; i++) {
        int down = 1 + column[i - 1];
        int diagonal = i + editd_penalty(prob, 0, i);
        column[i] = min(down, diagonal);
    }
}

/* editd_solve creates the buffers to store the columns of the edit
 * distance table, and calls editd_worker repeatedly to fill in the
 * table. It returns the solution to the edit distance instance in
 * prob.
 */
int editd_solve(editd *prob) {
    if (prob->min_len == 0) {
        return prob->max_len;
    }
    
    int *column = malloc(sizeof(int)*prob->min_len);
    int *prev = malloc(sizeof(int)*prob->min_len);
    editd_base_case(prob, prev);

    for (int i = 1; i < prob->max_len; i++) {
        editd_worker(prob, prev, column, i);
        int *temp = prev;
        prev = column;
        column = temp;
    }

    int ret = prev[prob->min_len - 1];
    free(column);
    free(prev);
    return ret;
}

/* random_string fills in the first len-1 chars in buf with
 * random lower case letters. buf[len-1] is filled with the NULL
 * terminator.
 */
void random_string(char *buf, int len) {
    static char *alphabet = "abcdefghijklmnopqrstuvwxyz";
    for (int i = 0; i < len - 1; i++) {
        buf[i] = alphabet[rand() % 26];
    }
    buf[len-1] = '\0';
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        printf("Usage: length\n");
        exit(1);
    }
    srand(time(NULL));

    int length = atoi(argv[1]);
    char *s1 = (char *) malloc(sizeof(char)*length);
    char *s2 = (char *) malloc(sizeof(char)*length);
    random_string(s1, length);
    random_string(s2, length);

    editd prob;
    editd_create(&prob, s1, s2);

    struct timeval start, end;
    gettimeofday(&start, NULL);
    int res = editd_solve(&prob);
    gettimeofday(&end, NULL);

     printf("%ld us\n", ((end.tv_sec * 1000000 + end.tv_usec)
          - (start.tv_sec * 1000000 + start.tv_usec)));

    printf("RESULT: %d\n", res);

    free(s1);
    free(s2);
}