#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/time.h>

#ifndef max
    #define max(a, b) ( ((a) > (b)) ? (a) : (b) )
#endif

#ifndef min
    #define min(a, b) ( ((a) < (b)) ? (a) : (b) )
#endif

#define THREAD_MAX 16

char *random_string(int len) {
    static char *alphabet = "abcdefghijklmnopqrstuvwxyz";
    char *ret = (char *) malloc(sizeof(char)*len + 1);
    if (ret == NULL) {
        printf("Malloc failed!\n");
        exit(1);
    }
    for (int i = 0; i < len; i++) {
        ret[i] = alphabet[rand() % 26];
    }
    ret[len] = 0;
    return ret;
}

typedef struct {
    int max_len;
    int min_len;
    char *max_str;
    char *min_str;
    int pthread_count;
    int chunk_size;
} editd;

int editd_penalty(editd *prob, int x, int y) {
    return (prob->max_str[x] == prob->min_str[y]) ? 0 : 1;
}


typedef struct {
    int *prev_row;
    int prev_row_len;
    int *current_row; // TODO: send row_len as well
    int current_len;
    int iteration;
    int start_idx;
    int stop_idx;
    editd *prob;
} chunk;

void *editd_worker(void *ci) {
    chunk *current = (chunk *) ci;
    printf("start_idx: %d, stop_idx: %d iteration: %d\n", current->start_idx, current->stop_idx, current->iteration);

    int *prev_row    = current->prev_row;
    int prev_row_len = current->prev_row_len;
    int *row         = current->current_row;
    int current_len  = current->current_len;
    int iteration    = current->iteration;
    int start_idx    = current->start_idx;
    int stop_idx     = current->stop_idx;
    editd *prob    = current->prob;
    int min_len = prob->min_len;
    int max_len = prob->max_len;

    int subproblem_count = (stop_idx - start_idx + 1)/2;
    for (int i = 0; i < subproblem_count; i++) {
        int x, y;
        int left_idx, down_idx, diagonal_idx;
        if (iteration < min_len) {
            x = i + start_idx/2;
            y = current_len - x - 1;
            down_idx = i*2 + start_idx;
            left_idx = down_idx - 2;
            diagonal_idx = down_idx - 1;
        } else {
            x = i + start_idx/2 + iteration - min_len + 1;
            y = min_len - start_idx/2 - i - 1;
            left_idx = i*2 + start_idx;
            down_idx = left_idx + 2;
            diagonal_idx = left_idx + 1;
        }

        int left, down, diagonal;
        if (left_idx < 0) {
            left = 2 + iteration;
        } else {
            left = prev_row[left_idx] + 1;
        }

        if (down_idx < prev_row_len) {
            down = prev_row[down_idx] + 1;
        } else {
            down = 2 + iteration;
        }
        
        int penalty = editd_penalty(prob, x, y);
        if (diagonal_idx < 0 || diagonal_idx >= prev_row_len) {
            diagonal = penalty + iteration;
        } else {
            diagonal = penalty + prev_row[diagonal_idx];
        }
        
        int dist = min(left, min(down, diagonal));
        //printf("left_idx: %d, down_idx: %d, diagonal_idx: %d\n", left_idx, down_idx, diagonal_idx);
        //printf("(%d, %d) left: %d, down: %d, diagonal: %d, penalty: %d, dist: %d\n", x, y, left, down, diagonal, penalty, dist);
        row[start_idx + i*2] = dist;
    }

    int merge_start_idx = 0;
    if (iteration >= min_len) {
        merge_start_idx = 2;
    }
    for (int i = 1; i < stop_idx - start_idx; i += 2) {
        row[start_idx + i] = prev_row[merge_start_idx + start_idx + i - 1];
    }
}

editd *editd_create(char *s1, char *s2, int pthread_count, int chunk_size) {
    if (pthread_count > THREAD_MAX) {
        printf("Too many threads: %d. Must be less than %d threads\n", pthread_count, THREAD_MAX);
    }

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
    editd *prob = (editd *) malloc(sizeof(editd));
    prob->max_str = max_str;
    prob->min_str = min_str;
    prob->max_len = max_len;
    prob->min_len = min_len;
    prob->pthread_count = pthread_count;
    prob->chunk_size = chunk_size;
    // printf("max_str: %s, max_len: %lu, min_str: %s, min_len: %lu\n", 
    //     prob->max_str, prob->max_len, prob->min_str, prob->min_len);
    return prob;
}

int editd_solve(editd *prob) {
    // create thread structs
    pthread_t *threads = malloc(sizeof(pthread_t)*prob->pthread_count);
    chunk chunks[THREAD_MAX];
    for (int i = 0; i < THREAD_MAX; i++) {
        chunks[i].prob = prob;
    }

    // create joinable attribute
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    int iterations = prob->max_len + prob->min_len - 1;
    int *prev_row = NULL;
    int prev_row_len = 0;
    for (int i = 0; i < iterations; i++) {
        printf("=================================\n");
        // current_len is the number of subproblems to solve this iterations
        int current_len;
        if (i < prob->min_len) {
            current_len = i + 1;
        } else if (i < prob->max_len) {
            current_len = prob->min_len;
        } else {
            current_len = iterations - i;
        }

        // create this iteration's result array
        int total_len = current_len*2 - 1;
        int *current_row = malloc(sizeof(int)*total_len);

        // calculate chunk size
        int chunk_size = max(total_len / prob->pthread_count, prob->chunk_size);
        // printf("chunk_size: %d\n", chunk_size);
        int leftover = 0;
        if (chunk_size < total_len) {
            leftover = total_len % chunk_size;
        }
        // printf("leftover: %d\n", leftover);
        chunk_size += chunk_size % 2;

        // start pthreads
        int pthread_idx = 0;
        int stop_idx = 0;
        for (int start_idx = 0; stop_idx < total_len; start_idx += chunk_size) {
            if (total_len - (start_idx + chunk_size) < prob->chunk_size) {
                stop_idx = total_len;
            } else {
                stop_idx = start_idx + chunk_size;
            }
            chunk *current_chunk = &chunks[pthread_idx];
            current_chunk->start_idx = start_idx;
            current_chunk->stop_idx = stop_idx;
            current_chunk->iteration = i;
            current_chunk->current_len = current_len;
            current_chunk->current_row = current_row;
            current_chunk->prev_row = prev_row;
            current_chunk->prev_row_len = prev_row_len;

            pthread_create(&threads[pthread_idx], &attr, editd_worker, (void *)&chunks[pthread_idx]);
            pthread_idx++;

        }

        // join pthreads
        for (int join_idx = 0; join_idx < pthread_idx; join_idx++) {
            int rc = pthread_join(threads[join_idx], NULL);
            if (rc) {
                printf("ERROR; return code from pthread_join() is %d\n", rc);
                exit(1);
            }
        }

        if (pthread_idx > prob->pthread_count) {
            printf("TOO MANY THREADS! %d\n", pthread_idx);
            exit(1);
        }

        // printf("current: ");
        // for (int p = 0; p < total_len; p++) {
        //     printf("%d ", current_row[p]);
        // }
        // printf("\n");

        if (prev_row != NULL) { 
            free(prev_row);
        }
        prev_row = current_row;
        prev_row_len = total_len;
    }
    free(threads);

    int ret = prev_row[0];
    free(prev_row);
    return ret;
}

int main(int argc, char *argv[]) {
    if (argc != 4) {
        printf("Usage: thread_count chunk_size length\n");
        exit(1);
    }
    srand(time(NULL));

    int pthread_count = atoi(argv[1]);
    int chunk_size = atoi(argv[2]);
    int length = atoi(argv[3]);

    char *s1 = random_string(length);
    char *s2 = random_string(length);
    //printf("s1: %s, s2: %s\n", s1, s2);

    editd *base = editd_create(s1, s2, pthread_count, chunk_size);

    int sum = 0;
    // warmup
    sum += editd_solve(base);

    struct timeval start, end;
    gettimeofday(&start, NULL);
    editd_solve(base);
    gettimeofday(&end, NULL);

     printf("%ld us\n", ((end.tv_sec * 1000000 + end.tv_usec)
          - (start.tv_sec * 1000000 + start.tv_usec)));

    printf("RESULT: %d\n", sum);
    free(s1);
    free(s2);
    free(base);
}