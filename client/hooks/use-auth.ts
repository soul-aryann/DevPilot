"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";


import { api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export function useCurrentUser(){
    return useQuery({
        queryKey: queryKeys.auth.me(),
        queryFn: () => api.me(),
         staleTime: 5 * 60 * 1000,
    retry: false,
    })
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useMutation({
    mutationFn: () => api.logout(),
    onSettled: async () => {
      queryClient.setQueryData(queryKeys.auth.me(), null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.auth.all });
      router.replace("/login");
    },
  });
}
