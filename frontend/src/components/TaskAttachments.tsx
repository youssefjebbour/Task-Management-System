import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, Paperclip, Trash2 } from 'lucide-react';
import { api, del, download } from '../lib/api';
import type { Task, Person } from '../lib/types';
import { relative } from '../lib/format';
import { Button, Confirm, ErrorState, FormError, Loading, useToast } from './ui';

type Attachment = {
  id: string;
  filename: string;
  sizeBytes: number;
  uploader: Person;
  createdAt: string;
  canDelete: boolean;
};

export function TaskAttachments({ task }: { task: Task }) {
  const client = useQueryClient(),
    toast = useToast(),
    input = useRef<HTMLInputElement>(null);
  const [removing, setRemoving] = useState<Attachment | null>(null);
  const [validation, setValidation] = useState('');
  const query = useQuery({
    queryKey: ['attachments', task.id],
    queryFn: () => api<Attachment[]>(`/tasks/${task.id}/attachments`),
  });
  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api(`/tasks/${task.id}/attachments`, { method: 'POST', body: form });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['attachments', task.id] });
      client.invalidateQueries({ queryKey: ['activity'] });
      toast('File attached');
    },
  });
  const save = useMutation({
    mutationFn: (attachment: Attachment) =>
      download(`/attachments/${attachment.id}/download`, attachment.filename),
    onError: (error) => toast(error.message, 'error'),
  });
  const remove = useMutation({
    mutationFn: () => del(`/attachments/${removing!.id}`),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['attachments', task.id] });
      client.invalidateQueries({ queryKey: ['activity'] });
      setRemoving(null);
      toast('Attachment removed');
    },
    onError: (error) => toast(error.message, 'error'),
  });
  return (
    <section className="drawer-section">
      <div className="section-title">
        <h3>Attachments</h3>
        {task.permissions.comment && (
          <Button variant="ghost" busy={upload.isPending} onClick={() => input.current?.click()}>
            <Paperclip size={14} />
            Attach file
          </Button>
        )}
      </div>
      <input
        ref={input}
        className="sr-only"
        type="file"
        aria-label="Upload attachment"
        accept=".png,.jpg,.jpeg,.pdf,.txt,.md,.csv"
        disabled={upload.isPending || !task.permissions.comment}
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          setValidation('');
          upload.reset();
          if (!file) return;
          if (!file.size || file.size > 10 * 1024 * 1024) {
            setValidation('Choose a non-empty file up to 10 MB.');
            return;
          }
          upload.mutate(file);
        }}
      />
      {task.permissions.comment && (
        <p className="muted attachment-help">
          PNG, JPEG, PDF, TXT, Markdown or CSV · Up to 10 MB per file
        </p>
      )}
      {validation && (
        <p className="form-error" role="alert">
          {validation}
        </p>
      )}
      <FormError error={upload.error} />
      {query.isPending ? (
        <Loading rows={2} />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : query.data.length === 0 ? (
        <p className="muted">No files attached yet.</p>
      ) : (
        <ul className="attachment-list">
          {query.data.map((a) => (
            <li key={a.id}>
              <Paperclip size={16} />
              <div>
                <strong title={a.filename}>{a.filename}</strong>
                <span>
                  {Math.max(1, Math.ceil(a.sizeBytes / 1024))} KB · {a.uploader.fullName} ·{' '}
                  {relative(a.createdAt)}
                </span>
              </div>
              <button
                className="icon-button"
                aria-label={`Download ${a.filename}`}
                disabled={save.isPending}
                onClick={() => save.mutate(a)}
              >
                <Download size={16} />
              </button>
              {a.canDelete && (
                <button
                  className="icon-button"
                  aria-label={`Delete ${a.filename}`}
                  onClick={() => setRemoving(a)}
                >
                  <Trash2 size={15} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <Confirm
        open={!!removing}
        onClose={() => setRemoving(null)}
        title="Delete this attachment?"
        description={`“${removing?.filename}” will be permanently removed.`}
        onConfirm={() => remove.mutate()}
        busy={remove.isPending}
      />
    </section>
  );
}
