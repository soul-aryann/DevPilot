import type { IndexStatus } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export function indexStatusLabel(status: IndexStatus) {
  switch (status) {
    case "READY":
      return "Ready";
    case "INDEXING":
      return "Indexing";
    case "FAILED":
      return "Failed";
    default:
      return "Not indexed";
  }
}
export function IndexStatusBadge({
  status,
  className,
}: {
  status: IndexStatus;
  className?: string;
}) {
  const variant =
    status === "READY"
      ? "default"
      : status === "FAILED"
        ? "destructive"
        : status === "INDEXING"
          ? "secondary"
          : "outline";

  return (
    <Badge variant={variant} className={cn(className)}>
      {status === "INDEXING" && (
        <span className="mr-1 size-1.5 animate-pulse rounded-full bg-current" />
      )}
      {indexStatusLabel(status)}
    </Badge>
  );
}
