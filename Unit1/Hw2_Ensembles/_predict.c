#include <stdbool.h>
#include <stdlib.h>
#include <memory.h>
#include <omp.h>
int __new_predict(
    float *x, 
    int len, 
    int num_features,
    int *is_leafs, 
    int *feature_ids, 
    float *thresholds, 
    float *answers_in_leaf,
    float *predictions,
    int *leaf_indices)
{
    int i = 0;
    int node_id = 0;
    for (int i = 0; i < len; ++i) {
        int node_id = 0;
        while (is_leafs[node_id] == 0) {
            if (x[num_features * i + feature_ids[node_id]] <= thresholds[node_id]) {
                node_id = 2 * node_id + 1;
            }
            else {
                node_id = 2 * node_id + 2;
            }
        }
        predictions[i] = answers_in_leaf[node_id];
        leaf_indices[i] = node_id;
    }
    return 0;
}
