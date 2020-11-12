package com.bignerdranch.android.photogallery;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PollJobService extends JobService {
    private static final String TAG = PollJobService.class.getSimpleName();
    private static final int JOB_ID = 1;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.i(TAG, "PollJobService стартовал c id: " + jobParameters.getJobId());
        Intent i = PollJobIntentService.newIntent(this);
        PollJobIntentService.enqueueWork(this, i);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.i(TAG, "PollJobService остановился");
        return false;
    }

    public static void startPollJobService(Context context, boolean isOn) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName componentName = new ComponentName(context, PollJobService.class);
        if (isOn) {
            JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                    //.setMinimumLatency(TimeUnit.SECONDS.toMillis(10))
                    .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                    .setPersisted(true)
                    .build();
            jobScheduler.schedule(jobInfo);
        } else {
            jobScheduler.cancel(JOB_ID);
        }
    }

    public static boolean isPollJobServiceOn(Context context) {
        JobScheduler scheduler = (JobScheduler)context
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == JOB_ID) {
                return true;
            }
        }
        return false;
    }
}