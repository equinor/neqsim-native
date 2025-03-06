#ifndef __NEQSIM_H
#define __NEQSIM_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

double calcWaterDewPoint(graal_isolatethread_t*, double, double);

double calcWaterInGas(graal_isolatethread_t*, double, double);

#if defined(__cplusplus)
}
#endif
#endif
