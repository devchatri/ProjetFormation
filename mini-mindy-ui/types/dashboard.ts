export interface PipelineMetricsType {
  totalJobs: number;
  successfulJobs: number;
  failedJobs: number;
  runningJobs: number;
}

export interface DagRunType {
  id: string;
  dagId: string;
  status: "success" | "running" | "failed";
}

export interface DataLakeStatsType {
  bronze: number;
  silver: number;
  gold: number;
}

export interface KafkaLagType {
  [topic: string]: number;
}
