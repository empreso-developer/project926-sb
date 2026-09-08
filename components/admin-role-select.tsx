'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useToast } from '@/hooks/use-toast';
import { updateProfileRoleAction } from '@/lib/actions/admin';
import type { UserRole } from '@/lib/types';

interface AdminRoleSelectProps {
  profileId: string;
  currentRole: UserRole;
}

export function AdminRoleSelect({ profileId, currentRole }: AdminRoleSelectProps) {
  const router = useRouter();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);

  async function onChange(value: string) {
    setLoading(true);
    try {
      await updateProfileRoleAction(profileId, value as UserRole);
      toast({ title: 'Role updated' });
      router.refresh();
    } catch (err) {
      toast({
        title: 'Could not update role',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <Select value={currentRole} onValueChange={onChange} disabled={loading}>
      <SelectTrigger className="h-8 w-32">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="customer">Customer</SelectItem>
        <SelectItem value="organizer">Organizer</SelectItem>
        <SelectItem value="admin">Admin</SelectItem>
      </SelectContent>
    </Select>
  );
}
